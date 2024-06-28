@file:Suppress("DuplicatedCode", "UNCHECKED_CAST")

package day3

import day3.AtomicArrayWithCAS2SingleWriter.Status.*
import java.util.concurrent.atomic.*
import kotlin.math.exp

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
        val cell = array[index]
        return when (cell) {
            is AtomicArrayWithCAS2SingleWriter<*>.CAS2Descriptor -> cell.getElement(index)
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
        val index1: Int, val expected1: E, val update1: E,
        val index2: Int, val expected2: E, val update2: E
    ) {
        val status = AtomicReference(UNDECIDED)

        fun apply() {
            if (!array.compareAndSet(index1, expected1, this)) {
                assert(status.get() == UNDECIDED)
                status.set(FAILED)
                return
            }

            if (!array.compareAndSet(index2, expected2, this)) {
                assert(array.get(index1) == expected1)
                array.set(index1, expected1)
                assert(status.get() == UNDECIDED)
                status.set(FAILED)
                return
            }

            assert(status.get() == UNDECIDED)
            status.set(SUCCESS)

            array.set(index1, update1)
            array.set(index2, update2)
        }

        fun getElement(index: Int): Any {
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