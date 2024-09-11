package day4

import kotlinx.atomicfu.*

// This implementation never stores `null` values.
class AtomicArrayWithDCSS<E : Any>(size: Int, initialValue: E) {
    private val array = atomicArrayOfNulls<Any>(size)

    init {
        // Fill array with the initial value.
        for (i in 0 until size) {
            array[i].value = initialValue
        }
    }

    fun get(index: Int): E? {
        return when (val curValue = array[index].value) {
            is AtomicArrayWithDCSS<*>.DCSSDescriptor -> {
                if (curValue.status.value == Status.SUCCESS) curValue.update1 as E?
                else curValue.expected1 as E?
            }

            else -> {
                curValue as E?
            }
        }
    }

    fun cas(index: Int, expected: E?, update: E?): Boolean {
        // TODO: the cell can store a descriptor
        while (true) {
            return when(val curValue = array[index].value) {
                is AtomicArrayWithDCSS<*>.DCSSDescriptor -> {
                    curValue.applyOperation()
                    continue
                }

                expected -> {
                    array[index].compareAndSet(expected, update)
                }

                else -> {
                    false
                }
            }
        }
    }

    fun dcss(
        index1: Int, expected1: E?, update1: E?,
        index2: Int, expected2: E?
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
        // TODO This implementation is not linearizable!
        // TODO Store a DCSS descriptor in array[index1].

        while (true) {
            when (val curValue = array[index1].value) {
                is AtomicArrayWithDCSS<*>.DCSSDescriptor -> {
                    curValue.applyOperation()
                    continue
                }
                else -> {
                    val descriptor = DCSSDescriptor(index1, expected1, update1, index2, expected2)
                    return if (array[index1].compareAndSet(expected1, descriptor)) {
                        descriptor.applyOperation()
                        descriptor.status.value == Status.SUCCESS
                    } else false
                }
            }
        }
    }

    private inner class DCSSDescriptor(
        val index1: Int, val expected1: E?, val update1: E?,
        val index2: Int, val expected2: E?
    ) {
        val status = atomic(Status.UNDECIDED)
        fun applyOperation() {
            while (true) {
                array[index1].compareAndSet(expected1, this)

                when (val resultValue = array[index1].value) {
                    this -> {
                        // do nothing, continue to perform operation
                    }
                    is AtomicArrayWithDCSS<*>.DCSSDescriptor -> {
                        resultValue.applyOperation()
                        continue
                    }
                    else -> {
                        status.compareAndSet(Status.UNDECIDED, Status.FAILED)
                        break
                    }
                }

                when (get(index2)) {
                    expected2 -> {
                        status.compareAndSet(Status.UNDECIDED, Status.SUCCESS)
                    }
                    else -> {
                        status.compareAndSet(Status.UNDECIDED, Status.FAILED)
                    }
                }

                when (status.value) {
                    Status.UNDECIDED -> {
                        status.compareAndSet(Status.UNDECIDED, Status.FAILED)
                        continue
                    }
                    Status.FAILED -> break
                    Status.SUCCESS -> {}
                }

                array[index1].compareAndSet(this, update1)
                if (array[index1].value == this) continue
            }

            if (status.value == Status.FAILED) {
                array[index1].compareAndSet(this, expected1)
            }
        }
    }

    private enum class Status {
        UNDECIDED, FAILED, SUCCESS
    }
}