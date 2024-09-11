@file:Suppress("DuplicatedCode", "UNCHECKED_CAST", "WHEN_ENUM_CAN_BE_NULL_IN_JAVA")

package day3

import day3.AtomicArrayWithCAS2Simplified.Status.*
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicReferenceArray


// This implementation never stores `null` values.
class AtomicArrayWithCAS2Simplified<E : Any>(size: Int, initialValue: E) {
    private val array = AtomicReferenceArray<Any?>(size)

    init {
        // Fill array with the initial value.
        for (i in 0 until size) {
            array[i] = initialValue
        }
    }

    fun get(index: Int): E {
        val result = when (val cell = array[index]) {
            is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor -> cell.getValue(index)
            else -> cell
        }

        return result as E
    }

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
        val descriptor = if (index1 < index2) {
            CAS2Descriptor(
                index1 = index1, expected1 = expected1, update1 = update1,
                index2 = index2, expected2 = expected2, update2 = update2
            )
        } else {
            CAS2Descriptor(
                index1 = index2, expected1 = expected2, update1 = update2,
                index2 = index1, expected2 = expected1, update2 = update1
            )
        }
        descriptor.apply()
        return descriptor.status.get() === SUCCESS
    }

    inner class CAS2Descriptor(
        private val index1: Int, private val expected1: E, private val update1: E,
        private val index2: Int, private val expected2: E, private val update2: E
    ) {
        val status: AtomicReference<Status> = AtomicReference(UNDECIDED)

        fun getValue(requestedIndex: Int): E {
            val status = status.get()
            return when (status) {
                UNDECIDED, FAILED -> if (index1 == requestedIndex) expected1 else expected2
                SUCCESS -> if (index1 == requestedIndex) update1 else update2
            }
        }

        fun apply() {
            val isInstalled = install()
            updateStatus(isInstalled)
            if (isInstalled) {
                updateCell()
            }
        }

        private fun install(): Boolean {
            if (!install(index1, expected1)) return false
            return install(index2, expected2)
        }

        private fun install(index: Int, expected: E): Boolean {
            if (status.get() != UNDECIDED) return false
            while (true) {
                when (val cell = array[index]) {
                    this -> return true
                    is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor -> cell.apply()
                    expected -> if (array.compareAndSet(index, expected, this)) return true
                    else -> return false // unexpected value
                }
            }
        }

        /**
         * Update array cells logically
         */
        private fun installDescriptor(): Boolean {
            val cas1 = array.compareAndSet(index1, expected1, this)
            if (!cas1) {
                return false
            }

            val cas2 = array.compareAndSet(index2, expected2, this)
            if (!cas2) {
                array.compareAndSet(index1, this, expected1) // rollback a first cell
                return false
            }

            return true
        }

        private fun updateStatus(isInstalled: Boolean) {
            status.compareAndSet(UNDECIDED, if (isInstalled) SUCCESS else FAILED)
        }

        /**
         * Update array cells physically
         */
        private fun updateCell() {
            // TODO: check status -> replace (`fun updatePhysically`)

            array.compareAndSet(index1, this, update1)
            array.compareAndSet(index2, this, update2)
        }

        private fun help() {
            val cell1 = array[index1]
            if (cell1 is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {

            }

            val cell2 = array[index2]
            if (cell2 is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {

            }
        }
    }

    enum class Status {
        UNDECIDED,
        SUCCESS,
        FAILED
    }
}