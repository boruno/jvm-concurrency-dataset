import kotlinx.atomicfu.*

class AtomicArrayNoAba<E : Any>(size: Int, initialValue: E) {
    private val a = Array(size) { Ref(initialValue) }

    fun get(index: Int): E {
        return a[index].value
    }

    fun set(index: Int, upd: E) {
        a[index].value = upd
    }

    fun cas(index: Int, expected: E, update: E) =
        a[index].v.compareAndSet(expected, update)

    private fun descriptify(a: Ref<out Any?>, expectA: Any?, updateA: Any?,
                                  b: Ref<out Any?>, expectB: Any?): Boolean {
        val descriptor = RDCSSDescriptor(a, expectA, updateA, b, expectB)
        return if (a.cas(expectA, descriptor)) {
            descriptor.complete()
            descriptor.outcome.value == OutcomeType.SUCCESS
        } else
            false
    }
    fun cas2(index1: Int, expected1: E, update1: E,
             index2: Int, expected2: E, update2: E): Boolean {
        if (index1 == index2) {
            return if (update1 == update2) {
                cas(index1, expected1, (expected1 as Int + 2) as E)
            } else {
                false
            }
        }
        val A: Ref<E>; val expectedA: E; val updateA: E
        val B: Ref<E>; val expectedB: E; val updateB: E
        if (index1 < index2) {
            A = a[index1]; expectedA = expected1; updateA = update1
            B = a[index2]; expectedB = expected2; updateB = update2
        } else {
            A = a[index2]; expectedA = expected2; updateA = update2
            B = a[index1]; expectedB = expected1; updateB = update1
        }
        val desc = CAS2Descriptor(A, expectedA, updateA, B, expectedB, updateB)
        return if (A.cas(expectedA, desc)) {
            desc.complete()
            desc.outcome.value == OutcomeType.SUCCESS
        } else false
    }

    inner class Ref<T>(initial: T) {
        val v = atomic<Any?>(initial)

        var value: T
            get()  {
                v.loop { cur ->
                    when(cur) {
                        is AtomicArrayNoAba<*>.Descriptor -> cur.complete()
                        else -> return cur as T
                    }
                }
            }
            set(upd) {
                v.loop { cur ->
                    when(cur) {
                        is AtomicArrayNoAba<*>.Descriptor -> cur.complete()
                        else -> if (v.compareAndSet(cur, upd))
                            return
                    }
                }
            }


        fun cas(expect: Any?, upd: Any?): Boolean {
            v.loop { cur ->
                when(cur) {
                    is AtomicArrayNoAba<*>.Descriptor -> cur.complete()
                    else -> {
                        if (cur != expect) return false
                        if (v.compareAndSet(cur, upd)) return true
                    }
                }
            }
        }
    }
    enum class OutcomeType {UNDECIDED, SUCCESS, FAIL}
    abstract inner class Descriptor {
        abstract val outcome : Ref<OutcomeType>
        abstract fun complete()
    }
    inner class RDCSSDescriptor(
        val a: Ref<out Any?>, val expectA: Any?, val updateA: Any?,
        val b: Ref<out Any?>, val expectB: Any?,
    ) : Descriptor() {
        override val outcome = Ref(OutcomeType.UNDECIDED)
        override fun complete() {
            val update: Any?
            if (b.value === expectB) {
                update = updateA
                outcome.cas(OutcomeType.UNDECIDED, OutcomeType.SUCCESS)
            } else {
                update = expectA
                outcome.cas(OutcomeType.UNDECIDED, OutcomeType.FAIL)
            }
            a.v.compareAndSet(this, update)
        }
    }

    inner class CAS2Descriptor(
        val a: Ref<out Any?>, val expectA: Any?, val updateA: Any?,
        val b: Ref<out Any?>, val expectB: Any?, val updateB: Any?,
    ) : Descriptor() {
        override val outcome = Ref(OutcomeType.UNDECIDED)

        override fun complete() {
            if (b.v.value != this)
                descriptify(b, expectB, this, outcome, OutcomeType.UNDECIDED)

            outcome.cas(OutcomeType.UNDECIDED, OutcomeType.SUCCESS)

            val finalA = if (outcome.value == OutcomeType.SUCCESS) updateA else expectA
            a.v.compareAndSet(this, finalA)

            val finalB = if (outcome.value == OutcomeType.SUCCESS) updateB else expectB
            b.v.compareAndSet(this, finalB)
        }
    }
}