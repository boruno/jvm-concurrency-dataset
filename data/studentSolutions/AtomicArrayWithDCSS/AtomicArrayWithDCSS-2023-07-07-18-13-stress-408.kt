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
        if (cellValue is AtomicArrayWithDCSS<*>.DCSSDescriptor) {
            val status = cellValue.status.value
            return when (status) {
                Status.SUCCESS -> {
                    cellValue.updateA
                }

                Status.UNDECIDED, Status.FAILED -> cellValue.expectedA
            } as E
        }
        return cellValue as E
    }

    fun cas(index: Int, expected: E, update: E): Boolean {
        while (true) {
            val value = array[index].value
            when (value) {
                is AtomicArrayWithDCSS<*>.DCSSDescriptor -> {
                    value.help()
                }

                expected -> {
                    return array[index].compareAndSet(expected, update)
                }

                else -> {
                    return false
                }
            }
        }
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
            val isDescriptorSet = trySetDescriptor()
            trySetStatus(isDescriptorSet)
            updateValues()
        }

        private fun trySetStatus(isDescriptorSet: Boolean) {
            val curB = array[indexB].value
            while (true) {
                when (curB) {
                    expectedB -> {
                        setStatus(
                            if (isDescriptorSet) {
                                Status.SUCCESS
                            } else {
                                Status.FAILED
                            }
                        )
                        break
                    }

                    is AtomicArrayWithDCSS<*>.DCSSDescriptor -> {
                        curB.help()
                    }

                    else -> {
                        setStatus(Status.FAILED)
                        break
                    }
                }
            }
        }

        fun help() {
            trySetStatus(true)
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

        private fun trySetDescriptor(): Boolean {
            while (true) {
                val currentA = array[indexA].value

                when (currentA) {
                    expectedA -> {
                        return setDescriptor(indexA, expectedA, this)
                    }

                    this -> {
                        return true
                    }

                    is AtomicArrayWithDCSS<*>.DCSSDescriptor -> {
                        currentA.help()
                    }

                    else -> return false
                }
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