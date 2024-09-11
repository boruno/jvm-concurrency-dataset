import kotlinx.atomicfu.*

class AtomicArrayNoAba<E>(size: Int, initialValue: E) {
    private val a = atomicArrayOfNulls<Ref<E>>(size)

    init {
        for (i in 0 until size) a[i].value = Ref(initialValue)
    }

    fun get(index: Int) = a[index].value!!.value
    fun cas(index: Int, expected: E, update: E) = a[index].value!!.reference.compareAndSet(expected, update)
    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        val firstIndex = if (index1 <= index2) index1 else index2
        val firstExpected = if (index1 <= index2) expected1 else expected2
        while (true) {
            val caS2Descriptor =
                if (index1 <= index2) CAS2Descriptor(
                    a[index1].value!!,
                    expected1,
                    update1,
                    a[index2].value!!,
                    expected2,
                    update2
                )
                else CAS2Descriptor(a[index2].value!!, expected2, update2, a[index1].value!!, expected1, update1)
            if (a[firstIndex].value!!.reference.compareAndSet(firstExpected, caS2Descriptor)) {
                caS2Descriptor.complete()
                return caS2Descriptor.outcome.value === Outcome.SUCCESS
            } else if (a[firstIndex].value!!.value != firstExpected) return false
        }
    }

    class Ref<TValue>(initial: TValue) {
        val reference = atomic<Any?>(initial)

        var value: TValue
            get() {
                reference.loop { currentValue ->
                    when (currentValue) {
                        is Descriptor -> currentValue.complete()
                        else -> return currentValue as TValue
                    }
                }
            }
            set(updateValue) {
                reference.loop { currentValue ->
                    when (currentValue) {
                        is Descriptor -> currentValue.complete()
                        else -> if (reference.compareAndSet(currentValue, updateValue)) return
                    }
                }
            }
    }

    private class RDCSSDescriptor<AValue, BValue>(
        val a: Ref<AValue>, val expectA: AValue, val updateA: AValue,
        val b: Ref<BValue>, val expectB: BValue
    ) : Descriptor() {
        override fun complete() {
            if (b.value === expectB) outcome.reference.compareAndSet(Outcome.UNDECIDED, Outcome.SUCCESS)
            else outcome.reference.compareAndSet(Outcome.UNDECIDED, Outcome.FAIL)
            val updateValue = if (outcome.value === Outcome.SUCCESS) updateA else expectA
            a.reference.compareAndSet(this, updateValue)
        }
    }

    private class CAS2Descriptor<AValue, BValue>(
        private val a: Ref<AValue>, private val expectA: AValue, private val updateA: AValue,
        private val b: Ref<BValue>, private val expectB: BValue, private val updateB: BValue,
    ) : Descriptor() {
        override fun complete() {
            while (true) {
                val rdcssDescriptor = RDCSSDescriptor(b, expectB, updateB, outcome, Outcome.UNDECIDED)
                if (b.reference.compareAndSet(expectB, rdcssDescriptor)) {
                    rdcssDescriptor.complete()
                    outcome.reference.compareAndSet(Outcome.UNDECIDED, Outcome.SUCCESS)
                } else {
                    outcome.reference.compareAndSet(Outcome.UNDECIDED, Outcome.FAIL)
                }
                if (outcome.value === Outcome.SUCCESS) {
                    a.reference.compareAndSet(this, updateA)
                    b.reference.compareAndSet(this, updateB)
                    break
                } else if (outcome.value === Outcome.FAIL) {
                    a.reference.compareAndSet(this, expectA)
                    b.reference.compareAndSet(this, expectB)
                    break
                }
            }
        }
    }

    private abstract class Descriptor {
        val outcome = Ref(Outcome.UNDECIDED)
        abstract fun complete()
    }

    private enum class Outcome {
        UNDECIDED, SUCCESS, FAIL
    }
}