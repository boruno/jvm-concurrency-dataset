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
        while(true) {
            val cur = array[index].value!!
            if (cur is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
                cur.apply()
                continue
            }
            return cur as E
        }
        // TODO: the cell can store CAS2Descriptor
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

        fun apply() {
            // TODO: Install the descriptor, update the status, and update the cells;
            // TODO: create functions for each of these three phases.
            while (true){
                install()
                status.value = SUCCESS
                updateCells()
            }

        }

        fun install() {
            val cell1 = array[index1].value
            val cell2 = array[index2].value
            // help descriptor if present
            if (cell1 is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor && cell1.status.value == UNDECIDED){
                if (array[cell1.index1].compareAndSet(cell1, cell1.update1) &&
                    array[cell1.index2].compareAndSet(cell1, cell1.update2))
                    cell1.status.value = SUCCESS
            }
            if (cell2 is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor && cell2.status.value == UNDECIDED){
                if (array[cell2.index1].compareAndSet(cell2, cell2.update1) &&
                    array[cell2.index2].compareAndSet(cell2, cell2.update2))
                    cell2.status.value = SUCCESS
            }

            if (!array[index1].compareAndSet(expected1, this)) {
                status.value = FAILED
                return
            }
            if (!array[index2].compareAndSet(expected2, this)) {
                array[index1].compareAndSet(this, expected1)
                status.value = FAILED
                return
            }
        }

        fun updateCells() {
            array[index1].compareAndSet(this, update1)
            array[index2].compareAndSet(this, update2)
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}
