//package day3

import day3.AtomicArrayWithCAS2Simplified.Status.*
import kotlinx.atomicfu.*


// This implementation never stores `null` values.
class AtomicArrayWithCAS2Simplified<E : Any>(size: Int, initialValue: E) {
    private val array = atomicArrayOfNulls<Any?>(size)

    init {
        // Fill array with the initial value.
        for (i in 0 until size) {
            array[i].value = initialValue
        }
    }

    fun get(index: Int): E {
        val value = array[index].value

        return if (value is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
            when (index) {
                value.index1 -> if (value.status.value == SUCCESS) value.update1 as E else value.expected1 as E
                value.index2 -> if (value.status.value == SUCCESS) value.update2 as E else value.expected2 as E
                else -> error("Wrong index")
            }
        } else {
            value as E
        }
    }

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
        val descriptor = CAS2Descriptor(
            index1 = index1, expected1 = expected1, update1 = update1,
            index2 = index2, expected2 = expected2, update2 = update2
        )
        descriptor.apply()
        return descriptor.status.value === SUCCESS
    }

    inner class CAS2Descriptor(
        val index1: Int,
        val expected1: E,
        val update1: E,
        val index2: Int,
        val expected2: E,
        val update2: E
    ) {
        val status = atomic(UNDECIDED)

        fun apply() {
            val installStatus = install()
            updateStatus(installStatus)
            updateCells()
        }

        private fun install(): Boolean {
            return if (index1 < index2) {
                installToIndx(index1, expected1) && installToIndx(index2, expected2)
            } else {
                installToIndx(index2, expected2) && installToIndx(index1, expected1)
            }
        }

        private fun installToIndx(index: Int, expected: E): Boolean {
            while (true) {
                if (array[index].compareAndSet(expected, this)) {
                    return true
                }

                val indxVal = array[index].value

                if (indxVal == expected) {
                    if (array[index].compareAndSet(expected, this)) {
                        return true
                    } else {
                        continue
                    }
                } else if (indxVal == this) {
                    return true
                } else if (indxVal is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
                    if (indxVal.status.value != SUCCESS) {
                        indxVal.apply()
                    }
                    continue
                } else {
                    if (indxVal != expected) {
                        return false
                    }
                    continue
                }

                return false
            }
        }


        private fun updateStatus(installed: Boolean) {
            if (installed) {
                this.status.compareAndSet(UNDECIDED, SUCCESS)
            } else {
                this.status.compareAndSet(UNDECIDED, FAILED)
            }
        }

        private fun updateCells() {
            if (this.status.value == SUCCESS) {
                array[index1].compareAndSet(this, update1)
                array[index2].compareAndSet(this, update2)
            }
            if (this.status.value == FAILED) {
                array[index1].compareAndSet(this, expected1)
                array[index2].compareAndSet(this, expected2)
            }
        }
    }


    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}