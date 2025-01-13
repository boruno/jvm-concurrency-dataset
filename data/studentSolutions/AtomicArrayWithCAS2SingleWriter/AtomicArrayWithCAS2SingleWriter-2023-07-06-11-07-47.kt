//package day3

import AtomicArrayWithCAS2SingleWriter.Status.*
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls

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
        // TODO: the cell can store CAS2Descriptor
        val maybeDescriptor = array[index].value
        return if (maybeDescriptor is AtomicArrayWithCAS2SingleWriter<*>.CAS2Descriptor) {
            when (maybeDescriptor.status.value) {
                UNDECIDED, FAILED -> {
                    if (index == maybeDescriptor.index1) {
                        maybeDescriptor.expected1
                    } else {
                        maybeDescriptor.expected2
                    }
                }

                SUCCESS -> {
                    if (index == maybeDescriptor.index1) {
                        maybeDescriptor.update1
                    } else {
                        maybeDescriptor.update2
                    }
                }
            }
        } else {
            maybeDescriptor
        } as E
    }

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
        // TODO: this implementation is not linearizable,
        // TODO: use CAS2Descriptor to fix it.
        // TODO: Note that only one thread can call CAS2!
        val descriptor = CAS2Descriptor(index1, expected1, update1, index2, expected2, update2)
        if (array[index1].compareAndSet(expected1, descriptor) && array[index2].compareAndSet(expected2, descriptor)) {
            descriptor.apply(SUCCESS)
        } else {
            descriptor.apply(FAILED)
        }

        if (array[index1].value != expected1 || array[index2].value != expected2) return false
        array[index1].value = update1
        array[index2].value = update2
        return true
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

        fun apply(newStatus: Status) {
            // TODO: install the descriptor, update the status, update the cells.
            when (newStatus) {
                SUCCESS -> {
                    if (status.compareAndSet(UNDECIDED, SUCCESS)) {
                        array[index1].compareAndSet(this, update1)
                        array[index2].compareAndSet(this, update2)
                    }
                }
                FAILED -> {
                    if (status.compareAndSet(UNDECIDED, FAILED)) {
                        array[index1].compareAndSet(this, expected1)
                        array[index2].compareAndSet(this, expected2)
                    }
                }
                else -> {}
            }

        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}