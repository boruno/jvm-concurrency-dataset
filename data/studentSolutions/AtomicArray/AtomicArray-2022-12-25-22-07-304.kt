import kotlinx.atomicfu.*

class AtomicArray(size: Int, initialValue: Int) {
    private val a = atomicArrayOfNulls<Ref<Int>>(size)

    init {
        for (i in 0 until size) a[i].value = Ref(initialValue)
    }

    fun get(index: Int) = a[index].value!!.value

    fun cas(index: Int, expected: Int, update: Int) = a[index].value!!.compareAndSet(expected, update)

    fun cas2(
        index1: Int, expected1: Int, update1: Int,
        index2: Int, expected2: Int, update2: Int
    ): Boolean {
        if (index1 == index2) {
            return cas(index1, expected1, update2) // todo maybe will be fixed
        }
        if (index1 < index2) {
            val desc = Cas2Descriptor(a[index1].value!!, expected1, update1, a[index2].value!!, expected2, update2)
            if (a[index1].value!!.compareAndSet(expected1, desc)) {
                return desc.complete()
            } else {
                return false
            }
        } else {
            val desc = Cas2Descriptor(a[index2].value!!, expected2, update2, a[index1].value!!, expected1, update1)
            if (a[index2].value!!.compareAndSet(expected2, desc)) {
                return desc.complete()
            } else {
                return false
            }
        }
    }
}

@Suppress("UNCHECKED_CAST")
private class Ref<T>(initial: T) {
    val v = atomic<Any?>(initial) // either T or Descriptor
    var value: T
        get() {
            v.loop { cur ->
                when (cur) {
                    is Descriptor -> cur.complete()
                    else -> return cur as T
                }
            }
        }
        set(upd) {
            v.loop { cur ->
                when (cur) {
                    is Descriptor -> cur.complete()
                    else -> if (v.compareAndSet(cur, upd)) return
                }
            }
        }

    fun compareAndSet(expect: Any?, update: Any?): Boolean {
        v.loop { cur ->
            when (cur) {
                is Descriptor -> cur.complete()
                else -> return v.compareAndSet(expect, update)
            }
        }
    }

//    fun cas2dcss(expect: Any?, update: Cas2Descriptor<T>): Boolean {
//        while (true) {
//            val curValue = v.value
//            if (curValue is Descriptor) {
//                curValue.complete()
//                continue
//            }
//            val desc = Cas2DcssDescriptor(this, expect, update)
//            return if (v.compareAndSet(expect, desc)) {
//                desc.complete()
//            } else {
//                false
//            }
//        }
//    }
}

private abstract class Descriptor {
    abstract fun complete(): Boolean
}

private class Cas2Descriptor<T>(
    val a: Ref<T>, val expectA: T, val updateA: T,
    val b: Ref<T>, val expectB: T, val updateB: T
) : Descriptor() {
    val status = atomic<Boolean?>(null)

    override fun complete(): Boolean {
        if (status.value == null) {
            val newStatus: Boolean
            if (b.v.value != this) {
                val desc = Cas2DcssDescriptor(b, expectB, this)
                if (b.compareAndSet(expectB, desc)) {
                    newStatus = desc.complete()
                } else {
                    newStatus = b.value == this
                }
            } else {
                newStatus = true
            }
            status.compareAndSet(null, newStatus)
        }

        if (status.value!!) { // if proceed
            a.v.compareAndSet(this, updateA)
            b.v.compareAndSet(this, updateB)
        } else { // if revert
            a.v.compareAndSet(this, expectA)
        }

        return status.value!!
    }
}

private class Cas2DcssDescriptor<T>(
    val a: Ref<T>, val expect: Any?, val update: Cas2Descriptor<T>
) : Descriptor() {
    val status = atomic<Boolean?>(null)

    override fun complete(): Boolean {
        val newStatus = update.status.value == null
        status.compareAndSet(null, newStatus)

        if (status.value!!) { // if proceed
            a.v.compareAndSet(this, update)
        } else { // if revert
            a.v.compareAndSet(this, expect)
        }

        return status.value!!
    }
}