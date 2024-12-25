@file:Suppress("DuplicatedCode")

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

        fun apply() {
            installDescriptor(index1, expected1)
            installDescriptor(index2, expected2)
            updateStatus()
            updateCells()
        }

        private fun installDescriptor(index: Int, expected: E) {
            val cell = array[index].value;


            if (cell is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor && cell != this) {
                cell.apply()
            }

            array[index].compareAndSet(expected, this)
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
                array[index1].compareAndSet(this, update1)
                array[index2].compareAndSet(this, update2)
            }
            else if (status.value == FAILED) {
                array[index1].compareAndSet(this, expected1)
                array[index2].compareAndSet(this, expected2)
            }
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}