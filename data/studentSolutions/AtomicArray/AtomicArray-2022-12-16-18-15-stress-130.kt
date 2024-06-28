import java.util.concurrent.atomic.AtomicReference

class AtomicArray<E>(size: Int, initialValue: E) {
    private val a = Array(size) { Ref(initialValue) }

//    init {
//        for (i in 0 until size) a[i].value = initialValue
//    }

    fun get(index: Int) =
        a[index].value

    fun cas(index: Int, expected: Int, update: Int) =
        a[index].cas(expected, update)

    fun cas2(
        index1: Int, expected1: Int, update1: Int,
        index2: Int, expected2: Int, update2: Int
    ): Boolean {
        if (index1 == index2) {
            return cas(index1, expected1, update2)
        }
        val desc = if (index1 > index2) {
            CASNDescriptor(
                arrayOf(
                    Triple(a[index1], expected1, update1),
                    Triple(a[index2], expected2, update2)
                )
            )
        } else {
            CASNDescriptor(
                arrayOf(
                    Triple(a[index2], expected2, update2),
                    Triple(a[index1], expected1, update1)
                )
            )
        }
        return desc.complete()
    }
}

private enum class Outcome {
    FAILED, UNDECIDED, SUCCEEDED
}

private class CASNDescriptor<E>(
    val entry: Array<Triple<Ref<E>, Any?, Any?>>
) : Descriptor() {
    val status = Ref(Outcome.UNDECIDED)
    override fun complete(): Boolean {
        if (status.v.get() == Outcome.UNDECIDED) {
            var _status = Outcome.SUCCEEDED
            for (i in 0 until entry.size) {
                if (_status != Outcome.SUCCEEDED) {
                    break
                }
                while (true) {
                    val element = entry[i]
                    val _val = RDCSSDescriptor(
                        element.first, element.second, this,
                        status, Outcome.UNDECIDED
                    ).RDCSS()
                    if (_val is CASNDescriptor<*>) {
                        if (_val != this) {
                            _val.complete()
                            continue
                        }
                    } else if (_val != element.second) {
                        _status = Outcome.FAILED
                    }
                    break
                }
            }
            status.v.compareAndSet(Outcome.UNDECIDED, _status)
        }
        val succeeded = status.v.get() == Outcome.SUCCEEDED
        for (element in entry) {
            element.first.v.compareAndSet(this, if (succeeded) element.third else element.second)
        }
        return succeeded
    }
}

private abstract class Descriptor {
    abstract fun complete(): Boolean
}

private class Ref<T>(initial: T) {
    val v = AtomicReference<Any?>(initial)

    var value: T
        get() {
            while (true) {
                when (val cur = v.get()) {
                    is Descriptor -> cur.complete()
                    else -> return cur as T
                }
            }
        }
        set(upd) {
            while (true) {
                when (val cur = v.get()) {
                    is Descriptor -> cur.complete()
                    else -> if (v.compareAndSet(cur, upd))
                        return
                }
            }
        }

    fun cas(expected: Any?, update: Any?): Boolean {
        while (true) {
            val old = v.compareAndExchange(expected, update)
            if (old == expected) {
                return true
            }
            if (old is Descriptor) {
                old.complete()
            } else {
                return false
            }
        }
    }
}

private class RDCSSDescriptor<A, B>(
    val a: Ref<A>, val expectA: Any?, val updateA: Any?,
    val b: Ref<B>, val expectB: Any?
) : Descriptor() {
    override fun complete(): Boolean {
        val update = if (b.v.get() == expectB)
            updateA else expectA
        return a.v.compareAndSet(this, update)
    }

    fun RDCSS(): Any? {
        var r: Any?
        do {
            r = a.v.compareAndExchange(expectA, this)
            if (r is RDCSSDescriptor<*, *>) {
                r.complete()
            }
        } while (r is RDCSSDescriptor<*, *>)
        if (r === expectA) {
            complete()
        }
        return r
    }
}

