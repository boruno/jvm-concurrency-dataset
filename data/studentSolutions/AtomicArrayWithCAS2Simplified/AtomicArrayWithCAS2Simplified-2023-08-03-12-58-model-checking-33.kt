package day3

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
        // TODO: the cell can store CAS2Descriptor
        val value = array[index].value
        return when (value) {
            is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor -> return when (value.status.value) {
                SUCCESS -> if (index == value.index1) value.update1   as E else value.update2   as E
                else ->    if (index == value.index1) value.expected1 as E else value.expected2 as E
            }
            else -> value as E
        }
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
        val index1: Int,
        val expected1: E,
        val update1: E,
        val index2: Int,
        val expected2: E,
        val update2: E
    ) {
        val status = atomic(UNDECIDED)

        fun apply() {
            // TODO: Install the descriptor, update the status, and update the cells;
            // TODO: create functions for each of these three phases.
            val (firstIndex, secondIndex) = if (index1 < index2) index1 to index2 else index2 to index1
            val (firstExpected, secondExpected) = if (index1 < index2) expected1 to expected2 else expected2 to expected1

            while (!array[firstIndex].compareAndSet(firstExpected, this)) {
                val firstActual = array[firstIndex].value
                if (firstActual is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
                    if (firstActual == this) break
                    firstActual.apply()
                }
                else if (firstActual != firstExpected) {
                    status.compareAndSet(UNDECIDED, FAILED)
                    break
                }
            }

            while (!array[secondIndex].compareAndSet(secondExpected, this)) {
                val secondActual = array[secondIndex].value
                if (secondActual is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
                    if (secondActual == this) break
                    secondActual.apply()
                }
                else if (secondActual != secondExpected) {
                    status.compareAndSet(UNDECIDED, FAILED)
                    break
                }
            }

            status.compareAndSet(UNDECIDED, SUCCESS)
            updateCellsSuccessOrFailed()
        }

        private fun updateCellsSuccessOrFailed() {
            check(status.value == SUCCESS || status.value == FAILED)
            if (status.value == SUCCESS) {
                array[index1].compareAndSet(this, update1)
                array[index2].compareAndSet(this, update2)
            } else {
                array[index1].compareAndSet(this, expected1)
                array[index2].compareAndSet(this, expected2)
            }
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}