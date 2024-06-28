@file:Suppress("DuplicatedCode", "FoldInitializerAndIfToElvis")

package day3

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
        if (index1 > index2) return cas2(index2, expected2, update2, index1, expected1, update1)
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
            if (status.value === UNDECIDED) {
//                val (id1, id2) = install()
                val id1 = tryInstallDescriptor(index1, expected1)
                val id2 = tryInstallDescriptor(index2, expected2)
                if (id1 && id2) {
                    status.compareAndSet(UNDECIDED, SUCCESS)
                } else {
                    status.compareAndSet(UNDECIDED, FAILED)
                }
                if (status.value == SUCCESS) {
                    array[index1].compareAndSet(this, update1)
                    array[index2].compareAndSet(this, update2)
                } else  {
                    array[index1].compareAndSet(this, expected1)
                    array[index2].compareAndSet(this, expected2)
                }
//                updateStatus(id1, id2)
            }
            updateCells()
        }

        private fun install(): Pair<Boolean, Boolean> {
            // dcss: value is expected and status is undecided
            val id1 = tryInstallDescriptor(index1, expected1)
            val id2 = tryInstallDescriptor(index2, expected2)
//            val id1 = array[index1].compareAndSet(expected1, this)
//            val id2 = array[index2].compareAndSet(expected2, this)
            return Pair(id1, id2)
        }

        private fun tryInstallDescriptor(index: Int, expected: E): Boolean {
            while (true) {
                val value = array[index].value
                when {
                    value === this -> return true
                    value is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor -> value.apply()
                    value === expected -> {
                        if (array[index].compareAndSet(expected, this)) return true
                    }
                    else -> return false
                }
            }
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
            } else  {
                array[index1].compareAndSet(this, expected1)
                array[index2].compareAndSet(this, expected2)
            }
        }


        fun read(index: Int): E? = when (index) {
            index1 -> if (status.value == SUCCESS) update1 else expected1
            index2 -> if (status.value == SUCCESS) update2 else expected2
            else -> null
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}