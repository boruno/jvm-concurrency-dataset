//package day4

import kotlinx.atomicfu.*

// This implementation never stores `null` values.
@Suppress("UNCHECKED_CAST")
class AtomicArrayWithDCSS<E : Any>(size: Int, initialValue: E) {
    private val array = atomicArrayOfNulls<Any>(size + 1)

    init {
        // Fill array with the initial value.
        for (i in 0 until size) {
            array[i].value = initialValue
        }
        array[size].value = null
    }


    fun get(index: Int): E? {
        // TODO: the cell can store a descriptor
        while (true) {
            val value = array[index].value ?: return null
            val eValue = value as? E
            if (eValue != null) return eValue
            (value as DCSSDescriptor<E>).applyOperation()
        }
    }

    fun cas(index: Int, expected: E?, update: E?): Boolean {
        // TODO: the cell can store a descriptor
//        while (true) {
//            val value = array[index].value
////            if (value == null) return
//        }
//        return array[index].compareAndSet(expected, update)
        return dcss(index, expected, update, array.size - 1, null)
    }

    fun dcss(
        index1: Int, expected1: E?, update1: E?,
        index2: Int, expected2: E?
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
        // TODO This implementation is not linearizable!
        // TODO Store a DCSS descriptor in array[index1].
//        if (array[index1].value != expected1 || array[index2].value != expected2) return false
//        array[index1].value = update1
//        return true

        while (true) {
            val value1 = array[index1].value
            if (value1 != null && (value1 as? E) == null) {
                (value1 as DCSSDescriptor<E>).applyOperation()
                continue
            }
            value1 as E?

            val value2 = array[index2].value
            if (value2 != null && (value2 as? E) == null) {
                (value2 as DCSSDescriptor<E>).applyOperation()
                continue
            }
            value2 as E?

            val descriptor = DCSSDescriptor(index1, value1, expected1, update1, index2, value2, expected2)
            descriptor.applyOperation()

            if (descriptor.status.value == Status.CHECK_FAILED) return false
            if (descriptor.status.value == Status.SUCCESS) return true
        }
    }

    private inner class DCSSDescriptor<E>(
        val index1: Int, val value1: E?, val expected1: E?, val update1: E?,
        val index2: Int, val value2: E?, val expected2: E?
    ) {
        val status = atomic(Status.UNDECIDED)

        fun applyOperation() {
            while (true) {
                if (array[index1].compareAndSet(value1, this) || array[index1].value == this) {
                    if (array[index2].compareAndSet(value2, this) || array[index2].value == this) {
                        if (value1 == expected1 && value2 == expected2) {
                            if (status.compareAndSet(Status.UNDECIDED, Status.SUCCESS) || status.value == Status.SUCCESS) {
                                array[index1].compareAndSet(this, update1)
                            }
                            else {
                                status.compareAndSet(Status.UNDECIDED, Status.FAILED)
                                array[index2].compareAndSet(this, value2)
                                array[index1].compareAndSet(this, value1)
                            }
                        }
                        else {
                            if (status.compareAndSet(Status.UNDECIDED, Status.CHECK_FAILED)) {
                                array[index2].compareAndSet(this, value2)
                                array[index1].compareAndSet(this, value1)
                            }
                            else {
                                check(status.value != Status.SUCCESS)
                                array[index2].compareAndSet(this, value2)
                                array[index1].compareAndSet(this, value1)
                            }
                        }
                        return
                    }
                    else {
                        if (status.compareAndSet(Status.UNDECIDED, Status.FAILED) || status.value == Status.FAILED) {
                            array[index1].compareAndSet(this, expected1)
                        }
                        else if (status.value == Status.SUCCESS) {
                            array[index1].compareAndSet(this, update1)
                        }
                        return
                    }
                }
                else {
                    status.compareAndSet(Status.UNDECIDED, Status.FAILED)
                    return
                }
            }
        }
    }

    private enum class Status {
        UNDECIDED, FAILED, CHECK_FAILED, SUCCESS
    }
}