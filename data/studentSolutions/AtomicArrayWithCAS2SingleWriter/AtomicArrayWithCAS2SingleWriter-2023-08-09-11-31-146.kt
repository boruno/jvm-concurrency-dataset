@file:Suppress("DuplicatedCode", "UNCHECKED_CAST")

//package day3

import day3.AtomicArrayWithCAS2SingleWriter.Status.*
import java.util.concurrent.atomic.*

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
        // TODO: the cell can store CAS2Descriptor
        val v = array[index]
        when (v) {
            is AtomicArrayWithCAS2SingleWriter<*>.CAS2Descriptor -> {
                if (v.status.get() == SUCCESS) {
                    val result = if (v.index1 == index) {
                        v.update1 as E
                    } else {
                        v.update2 as E
                    }
                    // help?
                    return result
                } else {
                    return if (v.index1 == index) {
                        v.expected1 as E
                    } else {
                        v.expected2 as E
                    }
                }
            }
            else -> return v as E
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
        val status = AtomicReference(UNDECIDED)

        fun apply() {
            if (array.compareAndSet(index1, expected1, this)) {
                if (array.compareAndSet(index2, expected2, this)) {
                    if (status.compareAndSet(UNDECIDED, SUCCESS)) {
                        array.compareAndSet(index1, this, update1)
                        array.compareAndSet(index2, this, update2)
                    }
                }
            }
            status.compareAndSet(UNDECIDED, FAILED)
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}