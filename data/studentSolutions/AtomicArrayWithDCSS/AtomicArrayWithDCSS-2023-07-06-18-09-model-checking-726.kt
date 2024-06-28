package day3

import kotlinx.atomicfu.*

// This implementation never stores `null` values.
class AtomicArrayWithDCSS<E : Any>(size: Int, initialValue: E) {
    private val array = atomicArrayOfNulls<Any?>(size)

    init {
        // Fill array with the initial value.
        for (i in 0 until size) {
            array[i].value = initialValue
        }
    }

    fun get(index: Int): E? {
        // TODO: the cell can store a descriptor
        // TODO: the cell can store CAS2Descriptor
        val cellValue = array[index].value
        if (cellValue is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
            val status = cellValue.status.value
            return when (status) {
                AtomicArrayWithCAS2Simplified.Status.SUCCESS -> {
                    if (index == cellValue.index1) {
                        cellValue.update1
                    } else {
                        cellValue.update2
                    }
                }

                AtomicArrayWithCAS2Simplified.Status.UNDECIDED, AtomicArrayWithCAS2Simplified.Status.FAILED -> if (index == cellValue.index1) {
                    cellValue.expected1
                } else {
                    cellValue.expected2
                }
            } as E
        }
        return cellValue as E

    }

    fun cas(index: Int, expected: E, update: E): Boolean {
        return array[index].compareAndSet(expected, update)
    }

    fun setDescriptor(index: Int, expected: E, descriptor: DCSSDescriptor): Boolean {
        return array[index].compareAndSet(expected, descriptor)
    }

    fun setValue(index: Int, expected: DCSSDescriptor, value: E): Boolean {
        return array[index].compareAndSet(expected, value)
    }

    fun dcss(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
        // TODO This implementation is not linearizable!
        // TODO Store a DCSS descriptor in array[index1].
        val dcssDescriptor = DCSSDescriptor(
            indexA = index1, expectedA = expected1, updateA = update1,
            indexB = index2, expectedB = expected2
        )
        dcssDescriptor.apply()

        return dcssDescriptor.status.value === Status.SUCCESS
    }

    inner class DCSSDescriptor(
        val indexA: Int,
        val expectedA: E,
        val updateA: E,
        val indexB: Int,
        val expectedB: E
    ) {
        val status = atomic(Status.UNDECIDED)

        fun apply() {
            if (status.value === Status.UNDECIDED) {

                trySetDescriptors()
            }

            updateValues()

        }

        private fun updateValues() {
            if (status.value === Status.SUCCESS) {
                setValue(indexA, this, updateA)
            }

            if (status.value === Status.FAILED) {
                setValue(indexA, this, expectedA)
            }
        }

        private fun trySetDescriptors() {
            val currentA = array[indexA].value
            val currentB = array[indexB].value

            if (currentA == expectedA) {
                val descriptorSet = setDescriptor(indexA, expectedA, this)
                if (descriptorSet) {
                    if (currentB == expectedB) {
                        setStatus(Status.SUCCESS)
                    } else {
                        setStatus(Status.FAILED)
                    }
                } else {
                    setStatus(Status.FAILED)
                }
            }

            if (currentA == this) {
                setStatus(Status.SUCCESS)
            }

            if (currentA is AtomicArrayWithDCSS<*>.DCSSDescriptor) {
                currentA.apply()
            }
        }

        private fun setStatus(newStatus: Status) {
            status.compareAndSet(Status.UNDECIDED, newStatus)
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}