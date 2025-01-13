//package day3

import kotlinx.atomicfu.*
import AtomicArrayWithDCSS.Status.*

// This implementation never stores `null` values.
class AtomicArrayWithDCSS<E : Any>(size: Int, initialValue: E) {
    private val array = atomicArrayOfNulls<Any?>(size)

    init {
        // Fill array with the initial value.
        for (i in 0 until size) {
            array[i].value = initialValue
        }
    }

    fun get(index: Int): E {
        // TODO: the cell can store a descriptor
        while (true) {
            val value = array[index].value
            if (value is AtomicArrayWithDCSS<*>.DCSSDescriptor) {
                val status = value.status.value
                if (status === SUCCESS) return value.update1 as E
                if (status === FAILED) return value.expected1 as E
                value.apply()
            } else {
                return value as E
            }
        }
    }

    fun cas(index: Int, expected: E, update: E): Boolean {
        // TODO: the cell can store a descriptor
        while (true) {
            val curState = array[index].value
            when {
                curState is AtomicArrayWithDCSS<*>.DCSSDescriptor -> {
                    curState.apply()
                }

                curState === expected -> {
                    if (array[index].compareAndSet(expected, update)) {
                        return true // successfully set
                    } else {
                        continue // retry
                    }
                }

                else -> {
                    return false // value, not expected
                }
            }
        }
    }

    fun dcss(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
        // TODO This implementation is not linearizable!
        // TODO Store a DCSS descriptor in array[index1].
        val descriptor = DCSSDescriptor(index1, expected1, update1, index2, expected2)
//        val installed = array[index1].compareAndSet(expected1, descriptor)
        val installed = descriptor.tryInstallDescriptor()
        if (!installed) return false
//        descriptor.tryInstallDescriptor()
        descriptor.apply()
        return descriptor.status.value == SUCCESS
    }

    inner class DCSSDescriptor(
        private val index1: Int,
        val expected1: E,
        val update1: E,
        private val index2: Int,
        private val expected2: E,
    ) {
        val status = atomic(UNDECIDED)

        private fun isExpected2(): Boolean {
            while (true) {
                val curState = array[index2].value
                when {
                    curState is AtomicArrayWithDCSS<*>.DCSSDescriptor -> {
                        val status = curState.status.value
                        if (status === SUCCESS) {
                            return curState.update1 === expected2
                        }
                        if (status === FAILED) {
                            return curState.expected1 === expected2
                        }
//                        curState.apply()
                        return false
                    }

                    curState === expected2 -> {
                        return true
                    }

                    else -> {
                        return false
                    }
                }
            }
        }

        fun apply() {
            // TODO: install the descriptor, update the status, update the cells.
            if (status.value == UNDECIDED) {
//                val success = /*tryInstallDescriptor() && */array[index2].value === expected2
                val success = get(index2) === expected2
//                val statusValue = if (isExpected2()) SUCCESS else FAILED
                val statusValue = if (success) SUCCESS else FAILED
                status.compareAndSet(UNDECIDED, statusValue)
            }

            updateValues()
        }

        fun tryInstallDescriptor(): Boolean {
            return tryInstallDescriptor(index1, expected1)
        }

        private fun tryInstallDescriptor(index: Int, expected: E): Boolean {
            while (true) {
                val curState = array[index].value
                when {
                    curState === this -> {
                        return true // already installed
                    }

                    curState is AtomicArrayWithDCSS<*>.DCSSDescriptor -> {
                        curState.apply()
                    }

                    curState === expected -> {
                        if (array[index].compareAndSet(expected, this)) {
                            return true // successfully installed
                        } else {
                            continue // retry
                        }
                    }

                    else -> {
                        return false // value, not expected
                    }
                }
            }
        }

        private fun updateValues() {
            if (status.value == SUCCESS) {
                array[index1].compareAndSet(this, update1)
            } else {
                array[index1].compareAndSet(this, expected1)
            }
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}