import kotlinx.atomicfu.*

class AtomicArrayNoAba<E>(size: Int, initialValue: E) {
    private val a = atomicArrayOfNulls<MyRef<E>>(size)

    init {
        for (i in 0 until size) a[i].value = MyRef(initialValue)
    }

    fun get(index: Int) = a[index].value!!.get()

    fun cas(index: Int, expected: E, update: E) =
        a[index].value!!.ref.compareAndSet(expected, update)

    fun cas2(index1: Int, expected1: E, update1: E,
             index2: Int, expected2: E, update2: E): Boolean {
        // сортируем против блокировок
        if (index1 <= index2) {
            return cas2(a[index1].value!!, expected1, update1, a[index2].value!!, expected2, update2)
        }

        return cas2(a[index2].value!!, expected2, update2, a[index1].value!!, expected1, update1)
    }

    private fun cas2(a: MyRef<Any?>, expectedA: E, updateA: E,
                     b: MyRef<Any?>, expectedB: E, updateB: E): Boolean {
        val descriptor = Cas2Descriptor(a, expectedA, updateA, b, expectedB, updateB)
        a.set(descriptor)
        descriptor.complete()

        return descriptor.outcome.value == DescriptorStatus.SUCCESS
    }
}

private interface Descriptor {
    fun complete()
}

class Cas2Descriptor (
    val a: MyRef<Any?>, val expectedA: Any?, val updateA: Any?,
    val b: MyRef<Any?>, val expectedB: Any?, val updateB: Any?
) : Descriptor {
    val outcome = atomic(DescriptorStatus.UNDECIDED)

    override fun complete() {
        if (outcome.value == DescriptorStatus.SUCCESS) {
            b.ref.compareAndSet(this, updateB)
            a.ref.compareAndSet(this, updateA)
        } else if (outcome.value == DescriptorStatus.FAIL) {
            b.ref.compareAndSet(this, expectedB)
            a.ref.compareAndSet(this, expectedA)
        }

        val rdcssDescriptor = RDCSSDescriptor(b, expectedB, this, DescriptorStatus.UNDECIDED)

        b.set(rdcssDescriptor)
        rdcssDescriptor.complete()

        if (rdcssDescriptor.outcome.value == DescriptorStatus.FAIL) {
            outcome.compareAndSet(DescriptorStatus.UNDECIDED, DescriptorStatus.FAIL)
            return
        }

        if (rdcssDescriptor.outcome.value == DescriptorStatus.SUCCESS) {
            outcome.compareAndSet(DescriptorStatus.UNDECIDED, DescriptorStatus.SUCCESS)
        }
    }
}

// почему я не могу в AtomicRef<Any?> послать AtomicRef<Status>? что то на контрвариантном?
class RDCSSDescriptor(
    private val a: MyRef<Any?>, private val expectA: Any?, private val updateA: Any?, private val expectB: DescriptorStatus)
    : Descriptor {
    val outcome = atomic(DescriptorStatus.UNDECIDED)
    private val b: AtomicRef<DescriptorStatus> = atomic(DescriptorStatus.UNDECIDED)



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

        val currentB = b.value
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