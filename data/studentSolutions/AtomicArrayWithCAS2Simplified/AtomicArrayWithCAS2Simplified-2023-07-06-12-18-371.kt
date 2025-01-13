//package day3

import AtomicArrayWithCAS2Simplified.Status.*
import kotlinx.atomicfu.*
import java.lang.IllegalStateException


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

        return if (cell is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
            cell as AtomicArrayWithCAS2Simplified<E>.CAS2Descriptor

            val (expected, updated) = when (index) {
                cell.index1 -> with(cell) {  expected1 to update1 }
                cell.index2 -> with(cell) {  expected2 to update2 }
                else -> throw IllegalStateException()
            }

            when (cell.status.value) {
                UNDECIDED, FAILED -> expected
                SUCCESS -> updated
            }
        } else {
            cell as E
        }

    }

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
        // TODO: this implementation is not linearizable,
        // TODO: use CAS2Descriptor to fix it.
        // TODO: Note that only one thread can call CAS2!
        val descriptor = CAS2Descriptor(
            index1, expected1, update1,
            index2, expected2, update2,
        )

        return descriptor.apply()
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

        fun apply(): Boolean {
            if (!array[index1].compareAndSet(expected1, this)) {
                // do we have another descriptor here?
                // we may have another descriptor here
                val descriptor = array[index1].value

                if (descriptor !is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
                    return false
                }

                descriptor as AtomicArrayWithCAS2Simplified<E>.CAS2Descriptor

                // what is the descriptor status here
                descriptor.progressWith2ndCell()
            }

            return progressWith2ndCell()
        }

        fun progressWith2ndCell(): Boolean {
            val hasSet2ndCell = array[index2].compareAndSet(expected2, this)
            if (!hasSet2ndCell) {
                // is there a descriptor there
                val maybeDescriptor = array[index2].value
                if (maybeDescriptor is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
                    maybeDescriptor as AtomicArrayWithCAS2Simplified<E>.CAS2Descriptor
                    maybeDescriptor.progressWith2ndCell()
                }
            }

            val descriptorStatus = if (hasSet2ndCell) SUCCESS else FAILED
            status.compareAndSet(UNDECIDED, descriptorStatus)

            return if (hasSet2ndCell) {
                // set the values physically
                applyValues()
                true
            } else {
                // rollback to previous values
                rollbackValues()
                false
            }
        }

        private fun applyValues() {
            array[index1].value = update1
            array[index2].value = update2
        }
        private fun rollbackValues() {
            array[index1].value = expected1
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}