@file:Suppress("DuplicatedCode", "FoldInitializerAndIfToElvis")

//package day3

import day3.AtomicArrayWithCAS2Simplified.Status.*
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls


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
        return ((cell as? AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor)?.read(index) ?: cell) as E
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
            val (id1, id2) = install()
            updateStatus(id1, id2)
            updateCells()
        }

        private fun install(): Pair<Boolean, Boolean> {
            if (status.value == UNDECIDED) {
                val id1 = array[index1].compareAndSet(expected1, this)
                val id2 = array[index2].compareAndSet(expected2, this)
                return Pair(id1, id2)

            }

            return false to false
        }

        private fun updateStatus(id1: Boolean, id2: Boolean) {
            if (id1 && id2) {
                status.compareAndSet(UNDECIDED, SUCCESS)
            } else {
                status.compareAndSet(UNDECIDED, FAILED)
            }
        }

        private fun updateCells() {
            if (status.value == SUCCESS) {
                array[index1].compareAndSet(this, update1)
                array[index2].compareAndSet(this, update2)
            } else if (status.value == FAILED) {

                if (!array[index1].compareAndSet(this, expected1)) {

                }
                array[index2].compareAndSet(this, expected2)
            }
        }


        fun read(index: Int): E? = when (index) {
            index1 -> if (status.value == SUCCESS) update1 else expected1
            index2 -> if (status.value == SUCCESS) update2 else expected2
            else -> null
        }


        fun helpFinish() {
            val id1 = array[index1].compareAndSet(expected1, this)
            if (!id1) {
                updateCells()
            }
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}