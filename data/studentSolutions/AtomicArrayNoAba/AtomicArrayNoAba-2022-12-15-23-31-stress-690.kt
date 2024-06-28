import java.util.concurrent.atomic.AtomicReference

class AtomicArrayNoAba<E>(size: Int, initialValue: E) {
    private val a = Array(size) { Ref(initialValue) }

//    init {
//        for (i in 0 until size) a[i].value = initialValue
//    }

    fun get(index: Int) =
        a[index].value

    fun cas(index: Int, expected: E, update: E) =
        a[index].v.compareAndSet(expected, update)

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        val desc = CASNDescriptor(
            arrayOf(
                Triple(a[index1], expected1, update1),
                Triple(a[index2], expected2, update2)
            )
        )
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
                while (true) {
                    val element = entry[i]
                    if (_status != Outcome.SUCCEEDED) {
                        break
                    }
                    val _val = RDCSSDescriptor(
                        element.first, element.second, this,
                        status, Outcome.UNDECIDED
                    ).RDCSS()
                    if (_val is CASNDescriptor<*>) {
                        if (_val !== this) {
                            _val.complete()
                            continue
                        }
                    } else if (_val !== element.second) {
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
                val cur = v.get()
                when (cur) {
                    is Descriptor -> cur.complete()
                    else -> return cur as T
                }
            }
        }
        set(upd) {
            while (true) {
                val cur = v.get()
                when (cur) {
                    is Descriptor -> cur.complete()
                    else -> if (v.compareAndSet(cur, upd))
                        return
                }
            }
        }
}

private class RDCSSDescriptor<A, B>(
    val a: Ref<A>, val expectA: Any?, val updateA: Any?,
    val b: Ref<B>, val expectB: Any?
) : Descriptor() {
    override fun complete(): Boolean {
        val update = if (b.v.get() === expectB)
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

