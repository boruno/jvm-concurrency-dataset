//package day3

import day3.AtomicArrayWithCAS2Simplified.Status.*
import kotlinx.atomicfu.*
import java.lang.IllegalStateException


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
        val cell = array[index]
        val item = cell.value
        if (item is AtomicArrayWithCAS2SingleWriter<*>.CAS2Descriptor) {
            if (item.index1 == index) {
                return if (item.status.value != AtomicArrayWithCAS2SingleWriter.Status.SUCCESS) item.expected1 as E else item.update1 as E
            } else if (item.index2 == index) {
                return if (item.status.value != AtomicArrayWithCAS2SingleWriter.Status.SUCCESS) item.expected2 as E else item.update2 as E
            } else {
                throw IllegalStateException("Incorrect descriptor $item for index $index")
            }
        } else {
            return item as E
        }
    }

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
        // TODO: this implementation is not linearizable,
        // TODO: use CAS2Descriptor to fix it.
        if (array[index1].value != expected1 || array[index2].value != expected2) return false

        val descriptor = CAS2Descriptor(index1, expected1, update1, index2, expected2, update2)
        return descriptor.apply()
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

        fun apply(): Boolean {
            if (array[index1].compareAndSet(expected1, this)) {
                if (array[index2].compareAndSet(expected2, this)) {
                    status.value = SUCCESS

                    array[index1].compareAndSet(this, update1)
                    array[index2].compareAndSet(this, update2)
                    return true
                }
                else {
                    array[index1].value = expected1
                    return false
                }
            }
            else {
                return false
            }
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}