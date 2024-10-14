@file:Suppress("DuplicatedCode", "UNCHECKED_CAST")

package day3

import day3.AtomicArrayWithCAS2SingleWriter.Status.*
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicReferenceArray

// This implementation never stores `null` values.
class AtomicArrayWithCAS2SingleWriter<E : Any>(size: Int, initialValue: E) {
    private val array = AtomicReferenceArray<Any?>(size)

    init {
        // Fill array with the initial value.
        for (i in 0 until size) {
            array[i] = initialValue
        }
    }

    fun get(index: Int): E {
        return when (val cell = array[index]) {
            is AtomicArrayWithCAS2SingleWriter<*>.CAS2Descriptor -> {
                val status = cell.status.get()

                @Suppress("WHEN_ENUM_CAN_BE_NULL_IN_JAVA")
                val result = when (status) {
                    UNDECIDED, FAILED -> if (cell.index1 == index) cell.expected1 else cell.expected2
                    SUCCESS -> if (cell.index1 == index) cell.update1 else cell.update2
                }
                result as E
            }

            else -> {
                array[index] as E
            }
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
        return descriptor.status.get() === SUCCESS
    }

    inner class CAS2Descriptor(
        val index1: Int, val expected1: E, val update1: E,
        val index2: Int, val expected2: E, val update2: E
    ) {
        val status: AtomicReference<Status> = AtomicReference(UNDECIDED)

        /**
         * Install the descriptor, update the status, and update the cells;
         * In this task, only one thread can call cas2(..), so cas2(..) calls cannot be executed concurrently.
         */
        fun apply() {
            val isInstalled = installDescriptor()
            updateStatus(isInstalled)
            if (isInstalled) {
                updateCell()
            }
        }

        private fun installDescriptor(): Boolean {
            val cas1 = array.compareAndSet(index1, expected1, this)
            val cas2 = array.compareAndSet(index2, expected2, this)
            return cas1 && cas2
        }

        private fun updateStatus(isInstalled: Boolean) {
            val newStatus = if (isInstalled) SUCCESS else FAILED
            status.compareAndSet(status.get(), newStatus)
        }

        private fun updateCell() {
            array.compareAndSet(index1, this, update1)
            array.compareAndSet(index2, this, update2)
        }
    }

    enum class Status {
        UNDECIDED,
        SUCCESS,
        FAILED
    }
}