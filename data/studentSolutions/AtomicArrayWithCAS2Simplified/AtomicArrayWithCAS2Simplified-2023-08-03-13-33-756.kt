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
        val cell = array[index].value
        if (cell is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
            if (index == cell.index1) {
                if (cell.status.value == SUCCESS) {
                    return cell.update1 as E
                }
                else {
                    return cell.expected1 as E
                }
            }
            if (index == cell.index2) {
                if (cell.status.value == SUCCESS) {
                    return cell.update2 as E
                }
                else {
                    return cell.expected2 as E
                }
            }
        }
        return cell as E
    }

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
        val descriptor = if (index1 < index2) CAS2Descriptor(
            index1 = index1, expected1 = expected1, update1 = update1,
            index2 = index2, expected2 = expected2, update2 = update2
        ) else CAS2Descriptor(
            index1 = index2, expected1 = expected2, update1 = update2,
            index2 = index1, expected2 = expected1, update2 = update1)
        descriptor.apply(true)
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

        fun apply(help: Boolean) {
            // TODO: Install the descriptor, update the status, and update the cells;
            // TODO: create functions for each of these three phases.
            // TODO: In this task, only one thread can call cas2(..),
            // TODO: so cas2(..) calls cannot be executed concurrently.

            if (help) {
                tryHelp()
//                install()
            }
//            else {
//            }
            install()
            updateStatus()
            updateValues()
        }

        private fun install() {
            if (status.value != UNDECIDED)
                return
            array[index1].compareAndSet(expected1, this)
            if (array[index1].value == this)
                array[index2].compareAndSet(expected2, this)
//            while (true) {
//                if (array[index1].compareAndSet(expected1, this)) {
//                    if (array[index2].compareAndSet(expected2, this)) {
//                        status.value = SUCCESS
//                        return
//                    } else {
//                        if (tryHelp(index2))
//                            continue
//                        status.value = FAILED
//                        array[index1].compareAndSet(this, expected1)
//                        return
//                    }
//                } else {
//                    if (tryHelp(index1))
//                        continue
//                    status.value = FAILED
//                    return
//                }
//            }
        }

        fun updateStatus() {
            if (array[index1].value == this && array[index2].value == this)
                status.compareAndSet(UNDECIDED, SUCCESS)
            else
                status.compareAndSet(UNDECIDED, FAILED)
        }

        fun updateValues() {
            if (status.value == SUCCESS) {
                array[index1].compareAndSet(this, update1)
                array[index2].compareAndSet(this, update2)
            }
            else {
                array[index1].compareAndSet(this, expected1)
                array[index2].compareAndSet(this, expected2)
            }
        }
        private fun tryHelp() {
            val cell1 = array[index1].value
            if (cell1 != this && cell1 is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
                cell1.apply(false)
            }
            val cell2 = array[index2].value
            if (cell2 != this && cell2 is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
                cell2.apply(false)
            }
        }
//        private fun setValues(descriptor: CAS2Descriptor) {
//            array[index1].compareAndSet(descriptor, update1)
//            array[index2].compareAndSet(descriptor, update2)
//        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}