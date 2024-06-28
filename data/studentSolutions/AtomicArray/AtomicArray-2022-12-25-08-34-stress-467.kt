import kotlinx.atomicfu.*

class AtomicArray<E>(size: Int, initialValue: E) {
    private val array = Array(size) { Ref(initialValue) }

    fun get(index: Int) =
        array[index].value

    fun cas(index: Int, expected: E, update: E) =
        array[index].cas(expected, update)

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean = if (index1 == index2) {
        if (expected1 == expected2 && update1 == update2) {
            cas(index1, expected1, update1)
        } else {
            false
        }
    } else if (index1 < index2) {
        cas2Internal(index1, expected1, update1, index2, expected2, update2)
    } else {
        cas2Internal(index2, expected2, update2, index1, expected1, update1)
    }

    private fun cas2Internal(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        val a = array[index1]
        val b = array[index2]
        val cas2Descriptor = Cas2Descriptor(a, expected1, update1, b, expected2, update2)
        if (!a.v.compareAndSet(expected1, cas2Descriptor)) {
            a.v.compareAndSet(cas2Descriptor, expected1)
            b.v.compareAndSet(cas2Descriptor, expected2)
            return false
        }
        cas2Descriptor.complete()
        return cas2Descriptor.outcome.v.value == Outcome.SUCCESS
    }

    internal interface Descriptor {
        fun complete()
    }

    internal enum class Outcome {
        UNDECIDED, SUCCESS, FAIL
    }

    internal class RDCSSDescriptor<A, B>(
        private val a: Ref<A>, private val expectA: A, private val updateA: Any?,
        private val b: Ref<B>, private val expectB: B,
    ) : Descriptor {
        val outcome: AtomicRef<Outcome> = atomic(Outcome.UNDECIDED)

        override fun complete() {
            if (b.value === expectB) {
                outcome.compareAndSet(Outcome.UNDECIDED, Outcome.SUCCESS)
            } else {
                outcome.compareAndSet(Outcome.UNDECIDED, Outcome.FAIL)
            }
            if (outcome.value == Outcome.SUCCESS) {
                a.v.compareAndSet(this, updateA)
            } else {
                a.v.compareAndSet(this, expectA)
            }
        }
    }

    internal class Cas2Descriptor<A, B>(
        private val a: Ref<A>, private val expectA: A, private val updateA: A,
        private val b: Ref<B>, private val expectB: B, private val updateB: B
    ) : Descriptor {
        val outcome: Ref<Outcome> = Ref(Outcome.UNDECIDED)

        @Suppress("SameParameterValue")
        private fun <A, B> rdcss(
            a: Ref<A>, expectA: A, updateA: Any?,
            b: Ref<B>, expectB: B,
        ): Boolean {
            val dcssDescriptor = RDCSSDescriptor(a, expectA, updateA, b, expectB)
            do {
                if (a.v.compareAndSet(expectA, dcssDescriptor)) {
                    dcssDescriptor.complete()
                    return dcssDescriptor.outcome.value == Outcome.SUCCESS
                }
                val curA = a.v.value
                if (curA is RDCSSDescriptor<*, *>) curA.complete()
            } while (curA is RDCSSDescriptor<*, *>)
            return false
        }

        override fun complete() {
            do {
                if (rdcss(b, expectB, this, outcome, Outcome.UNDECIDED) || b.v.value === this) {
                    outcome.v.compareAndSet(Outcome.UNDECIDED, Outcome.SUCCESS)
                    break
                }
                val curB = b.v.value
                if (curB is Cas2Descriptor<*, *>) curB.complete()
            } while (curB is Cas2Descriptor<*, *>)
            outcome.v.compareAndSet(Outcome.UNDECIDED, Outcome.FAIL)
            if (outcome.v.value == Outcome.SUCCESS) {
                a.v.compareAndSet(this, updateA)
                b.v.compareAndSet(this, updateB)
            } else {
                a.v.compareAndSet(this, expectA)
                b.v.compareAndSet(this, expectB)
            }
        }
    }

    internal class Ref<T>(initial: T) {
        val v = atomic<Any?>(initial)

        var value: T
            get() {
                v.loop { cur ->
                    @Suppress("UNCHECKED_CAST")
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

        fun cas(expect: Any?, update: Any?): Boolean {
//            return v.compareAndSet(expect, update)
            v.loop { cur ->
                when (cur) {
                    is Descriptor -> cur.complete()
                    else -> return v.compareAndSet(expect, update)
                }
            }
        }
    }
}