import kotlinx.atomicfu.*

class AtomicArray<E>(size: Int, initialValue: E) {
    private val array = atomicArrayOfNulls<MyRef<E>>(size)

    init {
        for (i in 0 until size) array[i].value = MyRef(initialValue)
    }

    fun get(index: Int) = array[index].value!!.get()

    fun cas(index: Int, expected: E, update: E) : Boolean {
        val currentValue = array[index].value!!.get()

        return currentValue == expected && array[index].value!!.ref.compareAndSet(expected, update)
    }

    fun cas2(index1: Int, expected1: E, update1: E,
             index2: Int, expected2: E, update2: E): Boolean {
        // сортируем против блокировок
        if (index1 <= index2) {
            return cas2(array[index1].value!!, expected1, update1, array[index2].value!!, expected2, update2)
        }

        return cas2(array[index2].value!!, expected2, update2, array[index1].value!!, expected1, update1)
    }

    private fun cas2(a: MyRef<Any?>, expectedA: E, updateA: E,
                     b: MyRef<Any?>, expectedB: E, updateB: E): Boolean {
        if (a === b) {
            return expectedA == expectedB && a.ref.compareAndSet(expectedB, updateB)
        }


        val descriptor = Cas2Descriptor(a, expectedA, updateA, b, expectedB, updateB)

        val currentAValue = a.get()
        if (currentAValue != expectedA || !a.ref.compareAndSet(expectedA, descriptor)) {
            return false
        }

        while (descriptor.outcome.get() == DescriptorStatus.UNDECIDED) {
            descriptor.complete()
        }

        return descriptor.outcome.get() == DescriptorStatus.SUCCESS
    }
}

private interface Descriptor {
    fun complete()
}

class Cas2Descriptor (
    val a: MyRef<Any?>, val expectedA: Any?, val updateA: Any?,
    val b: MyRef<Any?>, val expectedB: Any?, val updateB: Any?
) : Descriptor {
    val outcome = MyRef<DescriptorStatus>(DescriptorStatus.UNDECIDED)

    override fun complete() {
        val currentOutcome = outcome.get()
        if (currentOutcome == DescriptorStatus.SUCCESS) {
            b.ref.compareAndSet(this, updateB)
            a.ref.compareAndSet(this, updateA)
            return
        } else if (currentOutcome == DescriptorStatus.FAIL) {
            b.ref.compareAndSet(this, expectedB)
            a.ref.compareAndSet(this, expectedA)
            return
        }

        val currentBValue = b.ref.value
        if (currentBValue === this) {
            outcome.ref.compareAndSet(DescriptorStatus.UNDECIDED, DescriptorStatus.SUCCESS)
        }
        else if (currentBValue is Descriptor) {
            currentBValue.complete()
        }
        else {
            val rdcssDescriptor = RDCSSDescriptor(b, expectedB, this, outcome, DescriptorStatus.UNDECIDED)

            if (!b.ref.compareAndSet(expectedB, rdcssDescriptor)) {
                if (b.ref.value !is Descriptor) {
                    outcome.ref.compareAndSet(DescriptorStatus.UNDECIDED, DescriptorStatus.FAIL)
                }

                return
            }

            rdcssDescriptor.complete()
            if (rdcssDescriptor.outcome.value == DescriptorStatus.FAIL) {
                outcome.ref.compareAndSet(DescriptorStatus.UNDECIDED, DescriptorStatus.FAIL)
                return
            }

            if (rdcssDescriptor.outcome.value == DescriptorStatus.SUCCESS) {
                outcome.ref.compareAndSet(DescriptorStatus.UNDECIDED, DescriptorStatus.SUCCESS)
            }
        }
    }
}

// почему я не могу в AtomicRef<Any?> послать AtomicRef<Status>? что то на контрвариантном?
class RDCSSDescriptor(
    private val a: MyRef<Any?>, private val expectA: Any?, private val updateA: Any?,
    private val b: MyRef<DescriptorStatus>, private val expectB: DescriptorStatus)
    : Descriptor {
    val outcome = atomic(DescriptorStatus.UNDECIDED)

    override fun complete() {
        val curStatus = outcome.value
        if (curStatus != DescriptorStatus.UNDECIDED) {
            val updateTo = if (curStatus == DescriptorStatus.SUCCESS) {
                updateA
            } else {
                expectA
            }
            a.ref.compareAndSet(this, updateTo)
            return
        }

        val currentB = b.get()
        if (currentB === expectB)  {
            outcome.compareAndSet(DescriptorStatus.UNDECIDED, DescriptorStatus.SUCCESS)
        } else {
            outcome.compareAndSet(DescriptorStatus.UNDECIDED, DescriptorStatus.FAIL)
        }
    }
}

enum class DescriptorStatus { UNDECIDED, SUCCESS, FAIL }

class MyRef<out E>(init: Any?) {
    val ref : AtomicRef<Any?> = atomic(init)

    fun get() : E {
        while (true) {
            val value = ref.value
            if (value is Descriptor) {
                value.complete()
                continue
            }

            @Suppress("UNCHECKED_CAST")
            return value as E
        }
    }

    fun set(value: Any?) {
        while (true) {
            val curValue = ref.value
            if (curValue is Descriptor) {
                curValue.complete()
                continue
            }

            if (ref.compareAndSet(curValue, value)) {
                return
            }
        }
    }
}