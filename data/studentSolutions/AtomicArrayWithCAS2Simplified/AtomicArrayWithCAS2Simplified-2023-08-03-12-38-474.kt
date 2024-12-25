//package day3

import day3.AtomicArrayWithCAS2Simplified.Status.*
import day3.AtomicArrayWithCAS2SingleWriter.*
import kotlinx.atomicfu.*
import kotlin.math.min


// This implementation never stores `null` values.
class AtomicArrayWithCAS2Simplified<E : Any>(size: Int, initialValue: E) {
    private val array = atomicArrayOfNulls<Any?>(size)

    init {
        // Fill array with the initial value.
        for (i in 0 until size) {
            array[i].value = initialValue
        }
    }

    fun get(index: Int): E = when (val cell = array[index].value) {
        is AtomicArrayWithCAS2SingleWriter<*>.CAS2Descriptor -> {
            val updated  = if (cell.index1 == index) cell.update1   else cell.update2
            val expected = if (cell.index1 == index) cell.expected1 else cell.expected2
            if (cell.status.value === AtomicArrayWithCAS2SingleWriter.Status.SUCCESS) { updated } else { expected }
        }
        else -> cell
    } as E

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
        val descriptor =
            if (index1 < index2) {
            CAS2Descriptor(
            index1 = index1, expected1 = expected1, update1 = update1,
            index2 = index2, expected2 = expected2, update2 = update2)
        } else {
                CAS2Descriptor(
                    index1 = index2, expected1 = expected2, update1 = update2,
                    index2 = index1, expected2 = expected1, update2 = update1)
        }
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

//        fun isDescriptor() : AtomicArrayWithCAS2SingleWriter<*>.CAS2Descriptor? {
//            if (array[index1].value is AtomicArrayWithCAS2SingleWriter<*>.CAS2Descriptor)
//                return array[index1].value as AtomicArrayWithCAS2SingleWriter<*>.CAS2Descriptor
//            else if (array[index2].value is AtomicArrayWithCAS2SingleWriter<*>.CAS2Descriptor)
//                return array[index2].value as AtomicArrayWithCAS2SingleWriter<*>.CAS2Descriptor
//            return null
//        }

        fun apply() {
            // TODO: Install the descriptor, update the status, and update the cells;
            // TODO: create functions for each of these three phases.
            var old1 = array[index1].value

            while (true) {
                if (old1 is AtomicArrayWithCAS2SingleWriter<*>.CAS2Descriptor) {
                    old1.apply()
                    old1 = array[index1].value
                    continue
                }
                if (array[index1].compareAndSet(expected1, this)) { break }
                status.getAndSet(FAILED)
                return
            }
            var old2 = array[index2].value
            while (true) {
                if (old2 is AtomicArrayWithCAS2SingleWriter<*>.CAS2Descriptor) {
                    old2.apply()
                    old2 = array[index2].value
                    continue
                }
                if (array[index2].compareAndSet(expected2, this)) { break }
                array[index1].getAndSet(old1)
                status.getAndSet(FAILED)
                return
            }

            status.getAndSet(SUCCESS)

            array[index1].compareAndSet(this, update1)
            array[index2].compareAndSet(this, update2)
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}