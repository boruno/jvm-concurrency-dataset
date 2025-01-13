@file:Suppress("DuplicatedCode", "UNCHECKED_CAST")

//package day3

import AtomicArrayWithCAS2Simplified.Status.*
import java.util.concurrent.atomic.*


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
        val cell = array[index]
        return when (cell) {
            is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor -> cell.getElementAt(index)
            else -> cell
        } as E
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
        private val index1: Int,
        private val expected1: E,
        private val update1: E,
        private val index2: Int,
        private val expected2: E,
        private val update2: E
    ) {
        val status = AtomicReference(UNDECIDED)

        private val worklist
            get() = listOf(
                Triple(index1, expected1, update1),
                Triple(index2, expected2, update2)
            )

        fun apply() {
            val success = install()
            applyLogically(success)
            applyPhysically(success)
        }

        private fun install(): Boolean {
            val success = worklist.all { (idx, exp, _) -> array.compareAndSet(idx, exp, this) }
            return success
        }

        private fun applyLogically(success: Boolean) {
            status.compareAndSet(UNDECIDED, if (success) SUCCESS else FAILED)
        }

        private fun applyPhysically(success: Boolean) {
            for ((idx, exp, upd) in worklist) {
                array.compareAndSet(idx, this, if (success) upd else exp)
            }
        }

        fun getElementAt(index: Int): Any {
            @Suppress("WHEN_ENUM_CAN_BE_NULL_IN_JAVA")
            return when (status.get()) {
                UNDECIDED, FAILED -> when (index) {
                    index1 -> expected1
                    index2 -> expected2
                    else -> throw IllegalStateException()
                }
                SUCCESS -> when (index) {
                    index1 -> update1
                    index2 -> update2
                    else -> throw IllegalStateException()
                }
                else -> throw IllegalStateException()
            }
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}