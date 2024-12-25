//package day3

import day3.AtomicArrayWithCAS2SingleWriter.Status.*
import kotlinx.atomicfu.*
import java.lang.IllegalStateException

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
        if (index >= array.size) throw IllegalStateException()

        val cell = array[index].value
        if (cell is AtomicArrayWithCAS2SingleWriter<*>.CAS2Descriptor) {
            return cell.get(index) as E
        }
        return cell as E
    }

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
        val descriptor = CAS2Descriptor(
            indexA = index1, expectedA = expected1, updateA = update1,
            indexB = index2, expectedB = expected2, updateB = update2
        )
        descriptor.apply()
        return descriptor.status.value === SUCCESS
    }

    inner class CAS2Descriptor(
        private val indexA: Int,
        private val expectedA: E,
        private val updateA: E,

        private val indexB: Int,
        private val expectedB: E,
        private val updateB: E
    ) {
        val status = atomic(UNDECIDED)

        fun apply() {
            // TODO: Install the descriptor, update the status, and update the cells;
            // TODO: create functions for each of these three phases.
            // TODO: In this task, only one thread can call cas2(..),
            // TODO: so cas2(..) calls cannot be executed concurrently.

            if (install()) {
                status.value = SUCCESS
            } else {
                status.value = FAILED
            }
            updateCells()
        }

        private fun install(): Boolean {
            return array[indexA].compareAndSet(expectedA, this) &&
                    array[indexB].compareAndSet(expectedB, this)
        }

        private fun updateCells() {
            if (status.value == SUCCESS) {
                array[indexA].value = updateA
                array[indexB].value = updateB
            } else {
                array[indexA].value = expectedA
                array[indexB].value = expectedB
            }
        }

        fun get(i: Int): E {
            require(i == indexA || i == indexB)

            return when (i) {
                indexA -> if (status.value == SUCCESS) updateA else expectedA
                indexB -> if (status.value == SUCCESS) updateB else expectedB
                else -> error("Unknown index $i, A = ${indexA}, B = $indexB")
            }
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}