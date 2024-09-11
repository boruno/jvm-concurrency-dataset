package day3

import day3.AtomicArrayWithCAS2Simplified.Status.*
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

        if (!array[index1].compareAndSet(expected1, descriptor)) {
            return false
        }

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
            val hasSet2ndCell = array[index2].compareAndSet(expected2, this)
            val descriptorStatus = if (hasSet2ndCell)
                SUCCESS else FAILED
            status.compareAndSet(UNDECIDED, descriptorStatus)

            return if (hasSet2ndCell) {
                // set the values physically
                array[index1].value = update1
                array[index2].value = update2
                true
            } else {
                // rollback to previous values
                array[index1].value = expected1
                false
            }
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}