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


        private fun installAtIndex(index: Int, isOneAlreadyInstalled: Boolean): Boolean {
            if (status.value != UNDECIDED) {
                return false
            }

            val expected = if (index == index1) expected1 else expected2

            while (true) {
                if (array[index].compareAndSet(expected, this)) {
                    return true
                } else {
                    val cell = array[index].value
                    if(cell == this){
                        return true
                    } else if (cell is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
                        if(!cell.helpFromIndex(index)){
                            return false
                        }
                    } else {
                        if(isOneAlreadyInstalled)
                            cleanupFirst()
                        return false
                    }
                }
            }

        }

        private fun installDescriptor(): Boolean {
            val firstIndex = if (index1 < index2) index1 else index2
            val secondIndex = if (index1 < index2) index2 else index1

            return installAtIndex(firstIndex, false) && installAtIndex(secondIndex, true);
        }

        public fun helpFromIndex(index: Int): Boolean {

            val indexToHelp = if (index == index1) index2 else index1

            var success = false
            if(installAtIndex(indexToHelp, true)){
                updateStatus(SUCCESS)
                updateCellsSucessfully();
                success = true
            }
            return success;
        }

        private fun updateStatus(newStatus: Status): Boolean {
            return status.compareAndSet(UNDECIDED, newStatus)
        }

        private fun updateCellsSucessfully(){
            val firstIndex = if (index1 < index2) index1 else index2
            val secondIndex = if (index1 < index2) index2 else index1
            val firstUpdate = if (index1 < index2) update1 else update2
            val secondUpdate = if (index1 < index2) update2 else update1

            array[firstIndex].compareAndSet(this, firstUpdate)
            array[secondIndex].compareAndSet(this, secondUpdate)
        }

        private fun cleanupFirst(){
            val firstIndex = if (index1 < index2) index1 else index2
            val firstExpected = if (index1 < index2) expected1 else expected2
            array[firstIndex].compareAndSet(this, firstExpected)

        }

        private fun cleanupSecond(){
            val secondIndex = if (index1 < index2) index2 else index1
            val secondExpected = if (index1 < index2) expected2 else expected1
            array[secondIndex].compareAndSet(this, secondExpected)
        }

        private fun updateCells() {
            val curStatus = status.value

            if (curStatus == SUCCESS) {
                updateCellsSucessfully()
            } else if (curStatus == FAILED) {
                cleanupFirst()
                cleanupSecond()
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
                updateCellsSucessfully()
            }else {
                updateStatus(FAILED)
                cleanupFirst()
                cleanupSecond()
            }
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}