import kotlinx.atomicfu.*

class AtomicArrayNoAba<E>(size: Int, initialValue: E) {
    private val a = atomicArrayOfNulls<Any?>(size)

    init {
        for (i in 0 until size) a[i].value = initialValue
    }

    fun get(index: Int): E {
        val el = a[index].value
        if(el is Descriptor2<*>) {
            when(index) {
                el.index1 -> return el.expected1!! as E
                el.index2 -> return el.expected2!! as E
            }
        }
        return a[index].value!! as E
    }

    fun cas(index: Int, expected: Any?, update: Any?) =
        a[index].compareAndSet(expected, update)

    fun isDescriptor(index: Int): Boolean {
        return a[index].value is Descriptor2<*>
    }

    fun cas2(index1: Int, expected1: E, update1: E,
             index2: Int, expected2: E, update2: E): Boolean {

        var descriptor = Descriptor2(index1, expected1, update1, index2, expected2, update2)

        if(!isDescriptor(index1)) {
            if (!cas(index1, expected1, descriptor))
                return false
        } else descriptor = get(index1) as Descriptor2<E>

        if (descriptor.outcome == Outcome.UNDECIDED) {
            if(cas(index2, descriptor.expected2, descriptor))
                descriptor.outcome = Outcome.SUCCESS
            else
                descriptor.outcome = Outcome.FAIL
        }

        if (descriptor.outcome == Outcome.FAIL) {
            cas(index1, descriptor, descriptor.expected1)
            return false
        }

        cas(index1, descriptor, descriptor.update1)
        cas(index2, descriptor, descriptor.update2)
        return true
    }
}

private enum class Outcome {
    UNDECIDED, FAIL, SUCCESS
}

private class Descriptor2<E>(val index1: Int, val expected1: E, val update1: E, val index2: Int, val expected2: E, val update2: E, var outcome: Outcome = Outcome.UNDECIDED)