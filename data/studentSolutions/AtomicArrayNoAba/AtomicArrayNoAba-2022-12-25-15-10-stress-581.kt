import kotlinx.atomicfu.*

class AtomicArrayNoAba<E>(size: Int, initialValue: E) {
    private val a = atomicArrayOfNulls<Ref<E>>(size)

    init {
        for (i in 0 until size) a[i].value = Ref(initialValue)
    }

    fun get(index: Int) = a[index].value!!.value

    fun cas(index: Int, expected: E, update: E) = a[index].value!!.compareAndSet(expected, update)

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        if (index1 == index2) { // todo check?
            return cas(index1, expected1, update1)
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
        // todo !!!!!!! do lower index first
        // TODO this implementation is not linearizable,
        // TODO a multi-word CAS algorithm should be used here.
//        if (a[index1].value != expected1 || a[index2].value != expected2) return false
//        a[index1].value = update1
//        a[index2].value = update2
//        return true
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
                    else -> return v.value as T
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
            // valid in this task because after finish B will never be the same
        if (a.v.value != this || b.v.value != this) {
            status.compareAndSet(null, b.v.compareAndSet(expectB, this))
        } else {
            status.compareAndSet(null, true)
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