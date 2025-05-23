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
        while (true) {
            if (index1 == index2) {
                return if (expected1 == expected2) cas(index1, expected1, update2) else false
            }
            val firstIndex = if (index1 <= index2) index1 else index2
            val firstExpected = if (index1 <= index2) expected1 else expected2
            val caS2Descriptor =
                if (index1 <= index2)
                    CAS2Descriptor(a[index1].value!!, expected1, update1, a[index2].value!!, expected2, update2)
                else
                    CAS2Descriptor(a[index2].value!!, expected2, update2, a[index1].value!!, expected1, update1)
            if (a[firstIndex].value!!.reference.compareAndSet(firstExpected, caS2Descriptor)) { // 1 to 2
                caS2Descriptor.complete()
                return caS2Descriptor.outcome.value === Outcome.SUCCESS
            } else if (a[firstIndex].value!!.value != firstExpected) return false // 1 to 7
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
        val a: Ref<AValue>, val expectA: Any?, val updateA: Any?,
        val b: Ref<BValue>, val expectB: Any?
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
            if (b.reference.value !== this) {
                val rdcssDescriptor = RDCSSDescriptor(b, expectB, this, outcome, Outcome.UNDECIDED)
                if (b.reference.compareAndSet(expectB, rdcssDescriptor)) {
                    rdcssDescriptor.complete()
                    outcome.reference.compareAndSet(Outcome.UNDECIDED, Outcome.SUCCESS)
                } else outcome.reference.compareAndSet(Outcome.UNDECIDED, Outcome.FAIL)
            } else outcome.reference.compareAndSet(Outcome.UNDECIDED, Outcome.SUCCESS)

            if (outcome.value === Outcome.SUCCESS) {
                a.reference.compareAndSet(this, updateA)
                b.reference.compareAndSet(this , updateB)
            } else {
                a.reference.compareAndSet(this, expectA)
                b.reference.compareAndSet(this, expectB)
            }

//            while (true) {
//                val rdcssDescriptor = RDCSSDescriptor(b, expectB, updateB, outcome, Outcome.UNDECIDED)
//                if (b.reference.compareAndSet(expectB, rdcssDescriptor)) { // 2 to 3
//                    rdcssDescriptor.complete()
//                    outcome.reference.compareAndSet(Outcome.UNDECIDED, Outcome.SUCCESS)
//                } else { // 2 to 8
//                    if (b.reference.value is Descriptor) {
//                        if (b.reference.value != this) continue
//                        outcome.reference.compareAndSet(Outcome.UNDECIDED, Outcome.SUCCESS)
//                    } else outcome.reference.compareAndSet(Outcome.UNDECIDED, Outcome.FAIL)
//                }
//                if (outcome.value === Outcome.SUCCESS) { // 5 to 6
//                    a.reference.compareAndSet(this, updateA)
//                    b.reference.compareAndSet(this, updateB)
//                    break
//                } else if (outcome.value === Outcome.FAIL) { // 8 to 9
//                    a.reference.compareAndSet(this, expectA)
//                    b.reference.compareAndSet(this, expectB)
//                    break
//                }
//            }
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