//package day3

import AtomicArrayWithCAS2Simplified.Status.*
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
        // TODO: the cell can store CAS2Descriptor
        val cellState = array[index].value
        if (cellState is AtomicArrayWithCAS2SingleWriter<*>.CAS2Descriptor) {
            return cellState.get(index) as E
        }
        return cellState as E
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
        private val index1: Int,
        private val expected1: E,
        private val update1: E,
        private val index2: Int,
        private val expected2: E,
        private val update2: E
    ) {
        val status = atomic(UNDECIDED)


        private fun installAtIndex(index: Int): Boolean {
            if (status.value != UNDECIDED) {
                return false
            }

            val expected = if (index == index1) expected1 else expected2

            while (true) {
                val cell = array[index].value
                if (array[index].compareAndSet(expected, this)) {
                    return true
                } else if (cell is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor && cell != this){
                    cell.helpFromIndex(index)
                } else {
                    return false
                }
            }

        }

        private fun installDescriptor(): Boolean {
            if (status.value != UNDECIDED) {
                return false
            }

            val firstIndex = if (index1 < index2) index1 else index2
            val secondIndex = if (index1 < index2) index2 else index1

            return installAtIndex(firstIndex) && installAtIndex(secondIndex);

            /*
            var firstSuccess = false

            if (firstCell != this) {
                if (firstCell is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
                    firstCell.helpFromIndex(firstIndex)
                }
                if(array[firstIndex].compareAndSet(firstExpected, this)){
                    firstSuccess = true
                } else {
                    return false
                }
            } else {
                firstSuccess = true
            }

            var secondSuccess = false

            if(firstSuccess) {


                if (secondCell != this) {
                    if (secondCell is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
                        secondCell.helpFromIndex(secondIndex)
                    }
                    if (array[secondIndex].compareAndSet(secondExpected, this)) {
                        secondSuccess = true
                    }
                } else {
                    secondSuccess = true
                }
            }

            return firstSuccess && secondSuccess;
*/
        }

        public fun helpFromIndex(index: Int) {

            val indexToHelp = if (index == index1) index2 else index1

            if(installAtIndex(indexToHelp)){
                updateStatus(SUCCESS)
            }
            updateCells()
        }

        private fun updateStatus(newStatus: Status): Boolean {
            return status.compareAndSet(UNDECIDED, newStatus)
        }

        private fun updateCells() {
            val curStatus = status.value

            val firstIndex = if (index1 < index2) index1 else index2
            val secondIndex = if (index1 < index2) index2 else index1
            val firstExpected = if (index1 < index2) expected1 else expected2
            val secondExpected = if (index1 < index2) expected2 else expected1
            val firstUpdate = if (index1 < index2) update1 else update2
            val secondUpdate = if (index1 < index2) update2 else update1


            if (curStatus == SUCCESS) {
                array[firstIndex].compareAndSet(this, firstUpdate)
                array[secondIndex].compareAndSet(this, secondUpdate)
            } else if (curStatus == FAILED) {
                array[firstIndex].compareAndSet(this, firstExpected)
                array[secondIndex].compareAndSet(this, secondExpected)
            }
        }

        fun get(index: Int): E {
            if (status.value === SUCCESS) {
                return if (index == index1) update1 else update2
            } else {
                return if (index == index1) expected1 else expected2
            }
        }

        fun apply() {
            // TODO: Install the descriptor, update the status, and update the cells;
            // TODO: create functions for each of these three phases.
            if(installDescriptor()){
                updateStatus(SUCCESS)
            }else {
                updateStatus(FAILED)
            }
            updateCells()
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}