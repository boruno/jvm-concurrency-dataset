package day3

import day3.AtomicArrayWithCAS2SingleWriter.Status.*
import kotlinx.atomicfu.*

// This implementation never stores `null` values.
class AtomicArrayWithCAS2SingleWriter<E : Any>(size: Int, initialValue: E) {
    private val array = atomicArrayOfNulls<Any?>(size)

    init {
        // Fill array with the initial value.
        for (i in 0 until size) {
            array[i].value = initialValue
        }
    }

    fun get(index: Int): E {

        val cell = array[index].value;


        if (cell is AtomicArrayWithCAS2SingleWriter<*>.CAS2Descriptor) {
            if (cell.status.value == SUCCESS) {
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


            val installationSuccessful = installDescriptor()
            if (!installationSuccessful) {
                status.compareAndSet(UNDECIDED, FAILED)
            }
            else {
                updateStatus()
            }

            updateCells()



            // TODO: Install the descriptor, update the status, and update the cells;
            // TODO: create functions for each of these three phases.
            // TODO: In this task, only one thread can call cas2(..),
            // TODO: so cas2(..) calls cannot be executed concurrently.
        }

        private fun installDescriptor( ): Boolean {
            val val1 = array[index1].value
            val val2 = array[index2].value

            return array[index1].compareAndSet(val1, this)
                    && array[index2].compareAndSet(val2, this)

        }

        private fun updateStatus() {
            if (array[index1].value == expected1 && array[index2].value == expected2)
                status.compareAndSet(UNDECIDED, SUCCESS)
            else
                status.compareAndSet(UNDECIDED, FAILED)
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

    public enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}