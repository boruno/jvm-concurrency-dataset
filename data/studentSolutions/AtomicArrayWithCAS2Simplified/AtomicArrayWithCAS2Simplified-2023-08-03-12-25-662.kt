//package day3

import AtomicArrayWithCAS2Simplified.Status.*
import kotlinx.atomicfu.*


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
        return array[index].value as E
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
        val status: AtomicRef<Status> = atomic(Status.UNDECIDED)

        fun apply() {
            if (!install()) {
                status.compareAndSet(
                    Status.UNDECIDED,
                    Status.FAILED
                )
                return
            }
            if (!status.compareAndSet(
                    Status.UNDECIDED,
                    Status.SUCCESS
                )) {
                return
            }
            updateCellsBack()
        }

        private fun updateCellsBack() {
            if (!array[index1].compareAndSet(this, update1)) {
                return
            }
            if (!array[index2].compareAndSet(this, update2)) {
                return
            }
        }

        private fun install(): Boolean {
            if (!inst1()
                ||
                !inst2()
            ) {
                array[index1].compareAndSet(this, expected1)
                array[index2].compareAndSet(this, expected2)
                return false
            }
            return true
        }

        private fun inst1(): Boolean {
            if (array[index1].compareAndSet(expected1, this)) {
                return true
            }
            val v = array[index1].value
            if (v !is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
                return false
            }
            // help
            
            return  (v == this)
        }
        private fun inst2(): Boolean {
            if (array[index2].compareAndSet(expected2, this)) {
                return true
            }
            val v = array[index2].value
            if (v !is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
                return false
            }
            // help

            return  (v == this)
        }

        fun va(index:Int):E = if (index == index1) if (status.value == Status.SUCCESS) update1 else expected1
        else if (status.value == Status.SUCCESS) update2 else expected2
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}