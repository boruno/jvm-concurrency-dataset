//package day3

import day3.AtomicArrayWithCAS2Simplified.Status.*
import kotlinx.atomicfu.*


// This implementation never stores `null` values.
@Suppress("UNCHECKED_CAST")
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
        val value = array[index].value
        return if (value is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
            value.getInnerValue(index) as E
        } else {
            value as E
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
        while (true) {
            val cell1 = array[index1].value as? AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor
            val cell2 = array[index2].value as? AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor
            if (cell1 == null && cell2 == null)
                break
            else if (cell1 != null)
                cell1.apply()
            else
                cell2?.apply()
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

        fun apply() {
            if (array[index1].compareAndSet(expected1, this)) {
                if (array[index2].compareAndSet(expected2, this)) {
                    if (status.compareAndSet(UNDECIDED, SUCCESS)) {
                        array[index1].compareAndSet(this, update1)
                        array[index2].compareAndSet(this, update2)
                    } else {
                        return
                    }
                } else {
                    array[index1].compareAndSet(this, expected1)
                    status.compareAndSet(UNDECIDED, FAILED)
                }
            } else {
                status.compareAndSet(UNDECIDED, FAILED)
            }
        }

        fun getInnerValue(index : Int) : E {
            return if (status.value == SUCCESS) {
                if (index == index1) update1 else update2
            } else {
                if (index == index1) expected1 else expected2
            }
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}