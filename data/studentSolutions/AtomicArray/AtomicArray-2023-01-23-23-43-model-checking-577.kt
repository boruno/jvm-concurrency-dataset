import kotlinx.atomicfu.*

class AtomicArray<E>(size: Int, initialValue: E) {
    private val a = atomicArrayOfNulls<Ref<E>>(size)

    init {
        for (i in 0 until size) a[i].value = Ref(initialValue)
    }

    fun get(index: Int) =
        a[index].value!!.value

    fun set(index: Int, value: E) {
        a[index].value!!.value = value
    }

    fun cas(index: Int, expected: E, update: E) =
        a[index].value!!.cas(expected, update)

    fun cas2(index1: Int, expected1: E, update1: E,
             index2: Int, expected2: E, update2: E): Boolean {
        return if (index1 == index2) {
            @Suppress("UNCHECKED_CAST")
            cas(index2, expected2, update2)
        } else if (index1 < index2) {
            cas2Internal(
                a[index1].value!!, expected1, update1,
                a[index2].value!!, expected2, update2,
            )
        } else {
            cas2Internal(
                a[index2].value!!, expected2, update2,
                a[index1].value!!, expected1, update1,
            )
        }
    }

    private fun cas2Internal(
        a: Ref<E>, expectA: E, updateA: E,
        b: Ref<E>, expectB: E, updateB: E,
    ): Boolean {
        val descriptor = CAS2Descriptor(a, expectA, updateA, b, expectB, updateB)
        if (!a.cas(expectA, descriptor)) return false
        descriptor.complete()
        return descriptor.outcome.value == Outcome.SUCCESSFUL
    }

    private abstract class Descriptor {
        abstract fun complete()
    }

    private enum class Outcome {
        UNDECIDED, SUCCESSFUL, FAILED,
    }

    private class CAS2Descriptor<A, B>(
        val a: Ref<A>, val expectA: Any?, val updateA: Any?,
        val b: Ref<B>, val expectB: Any?, val updateB: Any?,
    ): Descriptor() {
        val outcome = atomic(Outcome.UNDECIDED)

        override fun complete() {
            // DCSS
            if (b.v.value === expectB && outcome.value == Outcome.UNDECIDED) {
                b.cas(expectB, this)
            }
            if (b.v.value === this) outcome.compareAndSet(Outcome.UNDECIDED, Outcome.SUCCESSFUL)
            else outcome.compareAndSet(Outcome.UNDECIDED, Outcome.FAILED)
            if (outcome.value == Outcome.SUCCESSFUL) {
                a.cas(this, updateA)
                b.cas(this, updateB)
            } else {
                a.cas(this, expectA)
                b.cas(this, expectB)
            }
        }
    }

    private class Ref<T>(initial: T) {
        val v = atomic<Any?>(initial)

        @Suppress("UNCHECKED_CAST")
        var value: T
            get()  {
                v.loop { cur ->
                    when(cur) {
                        is Descriptor -> cur.complete()
                        else -> return cur as T
                    }
                }
            }
            set(upd) {
                v.loop { cur ->
                    when(cur) {
                        is Descriptor -> cur.complete()
                        else -> if (v.compareAndSet(cur, upd))
                            return
                    }
                }
            }


        fun cas(expect: Any?, update: Any?) =
            v.compareAndSet(expect, update)
    }
}