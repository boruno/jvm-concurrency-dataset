@file:Suppress("DuplicatedCode")

package day3

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
        val cell = array[index].value;


        if (cell is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
            if (cell.status.value == SUCCESS) {
                if (index == cell.index1)
                    return cell.update1 as E
                return cell.update2 as E
            }
            else {
                if (index == cell.index1)
                    return cell.expected1 as E
                return cell.expected2 as E
            }
        }

        return cell as E;
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
        public val index1: Int,
        public val expected1: E,
        public val update1: E,
        public val index2: Int,
        public val expected2: E,
        public val update2: E
    ) {
        val status = atomic(UNDECIDED)

        fun installDescriptor(index: Int, expected: E): Boolean {
//
            if (status.value == FAILED || status.value == SUCCESS)
                return false

            if (array[index].compareAndSet(expected, this) )
                return true

            val cell = array[index].value
            if (cell is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor && cell != this) {
                cell.apply()
                return installDescriptor(index, expected)
            }

            status.compareAndSet(UNDECIDED, FAILED)
            return false;
        }

        fun apply() {

            if (index1 < index2) {
                installDescriptor(index1, expected1)
                installDescriptor(index2, expected2)
            }
            else {
                installDescriptor(index2, expected2)
                installDescriptor(index1, expected1)
            }

            updateStatus()
            updateCells()
        }

        private fun updateStatus() {
            if (array[index1].value == this && array[index2].value == this)
                status.compareAndSet(
                    UNDECIDED,
                    SUCCESS
                )
            else
                status.compareAndSet(
                    UNDECIDED,
                    FAILED
                )
        }

        private fun updateCells() {
            if (status.value == SUCCESS) {

                if (index1 < index2) {
                    array[index1].compareAndSet(this, update1)
                    array[index2].compareAndSet(this, update2)
                }
                else {
                    array[index2].compareAndSet(this, update2)
                    array[index1].compareAndSet(this, update1)
                }


            }
            else if (status.value == FAILED) {
                if (index1 < index2) {
                    array[index1].compareAndSet(this, expected1)
                    array[index2].compareAndSet(this, expected2)
                }
                else {
                    array[index2].compareAndSet(this, expected2)
                    array[index1].compareAndSet(this, expected1)
                }
            }
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}