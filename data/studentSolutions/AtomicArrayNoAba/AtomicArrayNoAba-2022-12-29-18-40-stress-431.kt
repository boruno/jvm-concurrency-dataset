import kotlinx.atomicfu.*

class AtomicArrayNoAba<E>(size: Int, initialValue: E) {
    private val a = atomicArrayOfNulls<Any>(size)

    init {
        for (i in 0 until size) a[i].value = initialValue
    }

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E {
        return when(val currentValue = a[index].value) {
            is Descriptor<*> -> {
                if (index == currentValue.index1)
                    currentValue.expected1 as E
                else
                    currentValue.expected2 as E
            }
            else -> currentValue as E
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun cas(index: Int, expected: E, update: E): Boolean {
        val currentValue = a[index].value
        if (currentValue is Descriptor<*>) {
            finishOperation(index, currentValue as Descriptor<E>)
        }
        return a[index].compareAndSet(expected, update)
    }

    fun cas2(index1: Int, expected1: E, update1: E,
             index2: Int, expected2: E, update2: E): Boolean {
        if (index1 == index2) {
//            if (expected1 == expected2 && update1 == update2)
//                return cas(index1, expected1, update1)
//            else
                return false
        }
        return if (index1 < index2)
            performCas2(index1, expected1, update1, index2, expected2, update2)
        else
            performCas2(index2, expected2, update2, index1, expected1, update1)
    }

    private fun performCas2(index1: Int, expected1: E, update1: E,
                    index2: Int, expected2: E, update2: E): Boolean {
        val descriptor = Descriptor(index1, expected1, update1, index2, expected2, update2)
        if (!tryPut(index1, descriptor, expected1))
            return descriptor.status == Status.SUCCESS
        if (!tryPut(index2, descriptor, expected2)) {
            if (descriptor.status == Status.SUCCESS)
                return true
            descriptor.casStatus(Status.UNDECIDED, Status.FAILED)
            a[descriptor.index1].compareAndSet(descriptor, descriptor.expected1)
            return false
        }
        performSuccess(descriptor)
        return true
    }

    @Suppress("UNCHECKED_CAST")
    private fun tryPut(index: Int, descriptor: Descriptor<E>, expected: E): Boolean {
        if (a[index].compareAndSet(expected, descriptor))
            return true
        return when (val currentValue = a[index].value) {
            is Descriptor<*> -> {
                finishOperation(index, currentValue as Descriptor<E>)
                a[index].compareAndSet(expected, descriptor)
            }
            else -> false
        }
    }

    private fun finishOperation(index: Int, descriptor: Descriptor<E>) {
        if (descriptor.status == Status.FAILED) {
            a[descriptor.index1].compareAndSet(descriptor, descriptor.expected1)
            a[descriptor.index2].compareAndSet(descriptor, descriptor.expected2)
        } else if (descriptor.status == Status.SUCCESS) {
            a[descriptor.index1].compareAndSet(descriptor, descriptor.update1)
            a[descriptor.index2].compareAndSet(descriptor, descriptor.update2)
        } else {
            if (descriptor.index1 == index) {
                if (!tryPut(descriptor.index2, descriptor, descriptor.expected2)) {
                    descriptor.casStatus(Status.UNDECIDED, Status.FAILED)
                    a[descriptor.index1].compareAndSet(descriptor, descriptor.expected1)
                } else {
                    performSuccess(descriptor)
                }
            } else {
                performSuccess(descriptor)
            }
        }
    }

    private fun performSuccess(descriptor: Descriptor<E>) {
        descriptor.casStatus(Status.UNDECIDED, Status.SUCCESS)
        a[descriptor.index1].compareAndSet(descriptor, descriptor.update1)
        a[descriptor.index2].compareAndSet(descriptor, descriptor.update2)
    }
}

class Descriptor<E>(
    val index1: Int,
    val expected1: E,
    val update1: E,
    val index2: Int,
    val expected2: E,
    val update2: E,
    status: Status = Status.UNDECIDED
) {

    val status: Status
        get() = _status.value
    private val _status = atomic(status)

    fun casStatus(expected: Status, update: Status) =
        _status.compareAndSet(expected, update)
}

enum class Status {
    UNDECIDED, SUCCESS, FAILED
}
