import kotlinx.atomicfu.*

class AtomicArray<E : Any>(size: Int, initialValue: E) {
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
                cas(index1, expected1, update1)
            } else {
                false
            }
        }
        val descriptor = if (index1 < index2)
            CAS2Descriptor(a[index1], expected1, update1, a[index2], expected2, update2)
        else
            CAS2Descriptor(a[index2], expected2, update2, a[index1], expected1, update1)

        return if (descriptor.a.cas(descriptor.expectA, descriptor)) {
            while (descriptor.outcome.value == OutcomeType.UNDECIDED)
                descriptor.complete()
            descriptor.outcome.value == OutcomeType.SUCCESS
        } else false
    }

    inner class Ref<T>(initial: T) {
        // must be any for descriptor and val swap
        val v = atomic<Any?>(initial)

        var value: T
            get()  {
                v.loop { cur ->
                    when(cur) {
                        is AtomicArray<*>.Descriptor -> cur.complete()
                        else -> return cur as T
                    }
                }
            }
            set(upd) {
                v.loop { cur ->
                    when(cur) {
                        is AtomicArray<*>.Descriptor -> cur.complete()
                        else -> if (v.compareAndSet(cur, upd))
                            return
                    }
                }
            }


        fun cas(expect: Any?, upd: Any?): Boolean {
            v.loop { cur ->
                when(cur) {
                    is AtomicArray<*>.Descriptor -> cur.complete()
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
        // could be atomic but custom func need Ref, might change
        abstract val outcome : Ref<OutcomeType>
        abstract fun complete()
    }
    inner class RDCSSDescriptor(
        val a: Ref<out Any?>, val expectA: Any?, val updateA: Any?,
        val b: Ref<out Any?>, val expectB: Any?,
    ) : Descriptor() {
        override val outcome = Ref(OutcomeType.UNDECIDED)
        override fun complete() {
            if (b.value == expectB) outcome.cas(OutcomeType.UNDECIDED, OutcomeType.SUCCESS)
            else outcome.cas(OutcomeType.UNDECIDED, OutcomeType.FAIL)
            val update: Any? = if (outcome.value==OutcomeType.SUCCESS) updateA else expectA
            a.v.compareAndSet(this, update)
        }
    }

    inner class CAS2Descriptor(
        val a: Ref<out Any?>, val expectA: Any?, val updateA: Any?,
        val b: Ref<out Any?>, val expectB: Any?, val updateB: Any?,
    ) : Descriptor() {
        override val outcome = Ref(OutcomeType.UNDECIDED)

        override fun complete() {
            if (b.v.value != this) {
                descriptify(b, expectB, this, outcome, OutcomeType.UNDECIDED)
            }

            outcome.cas(OutcomeType.UNDECIDED,if (b.v.value == this) OutcomeType.SUCCESS else OutcomeType.FAIL)

            val finalA = if (outcome.value == OutcomeType.SUCCESS) updateA else expectA
            // do not change for cas else stackoverflow
            a.v.compareAndSet(this, finalA)

            val finalB = if (outcome.value == OutcomeType.SUCCESS) updateB else expectB
            // do not change for cas else stackoverflow
            b.v.compareAndSet(this, finalB)
        }
    }
}