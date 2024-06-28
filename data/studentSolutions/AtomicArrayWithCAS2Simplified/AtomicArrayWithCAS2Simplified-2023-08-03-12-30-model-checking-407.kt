package day3

import day3.AtomicArrayWithCAS2Simplified.Status.*
import kotlinx.atomicfu.*
import java.util.NoSuchElementException


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
        return when(val value = array[index].value) {
            is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor -> value[index] as E
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
        private val index1: Int,
        private val expected1: E,
        private val update1: E,
        private val index2: Int,
        private val expected2: E,
        private val update2: E
    ) {
        val status = atomic(UNDECIDED)
        val pairs = listOf(
            index1 to expected1,
            index2 to expected2
        ).sortedBy { it.first }

        operator fun get(i: Int): E = when (status.value) {
            UNDECIDED, FAILED -> when (i) {
                index1 -> expected1
                index2 -> expected2
                else -> throw NoSuchElementException()
            }

            SUCCESS -> when (i) {
                index1 -> update1
                index2 -> update2
                else -> throw NoSuchElementException()
            }
        }

        fun apply() {
            installDescriptor()
            updateStatus()
            updateCells()
        }

        private fun installDescriptor() {
            pairs.all { (index, expected) ->
                array[index].compareAndSet(expected, this)
            }
        }

        private fun updateStatus() {
            val success = pairs.all { (index, _) -> checkIndex(index) }
            val newStatus = if (success) SUCCESS else FAILED
            status.compareAndSet(UNDECIDED, newStatus)
        }

        private fun checkIndex(i: Int): Boolean {
            when (val actual = array[i].value) {
                this -> return true
                is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor -> actual.apply()
            }
            return false
        }

        private fun updateCells() {
            when (status.value) {
                SUCCESS -> {
                    array[index1].compareAndSet(this, update1)
                    array[index2].compareAndSet(this, update2)
                }
                FAILED, UNDECIDED -> {
                    array[index1].compareAndSet(this, expected1)
                    array[index2].compareAndSet(this, expected2)
                }
            }
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}