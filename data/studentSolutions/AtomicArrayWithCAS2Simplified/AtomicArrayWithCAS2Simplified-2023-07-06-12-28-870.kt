package day3

import day3.AtomicArrayWithCAS2Simplified.Status.*
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
        val value = array[index].value

        if (value is AtomicArrayWithCAS2SingleWriter<*>.CAS2Descriptor) {
            return value.read(index) as E
        }

        return value as E
    }

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
        // TODO: this implementation is not linearizable,
        // TODO: use CAS2Descriptor to fix it.
        return if (index1 < index2) {
            CAS2Descriptor(
                index1, expected1, update1,
                index2, expected2, update2
            ).apply()
        } else {
            CAS2Descriptor(
                index2, expected2, update2,
                index1, expected1, update1,
            ).apply()
        }
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

        private fun revertIfNeed(): Boolean {
            if (status.value == FAILED) {
                array[index1].compareAndSet(this, expected1)
                array[index2].compareAndSet(this, expected2)
                return true
            }
            return false
        }

        private fun finishIfNeed(): Boolean {
            if (status.value == SUCCESS) {
                array[index1].compareAndSet(this, update1)
                array[index2].compareAndSet(this, update2)
                return true
            }
            return false
        }

        fun apply(): Boolean {
            // TODO: install the descriptor, update the status, update the cells.

            if (!addDescriptor(index1, expected1)) return false

            if (addDescriptor(index2, expected2)) {
                this.status.compareAndSet(UNDECIDED, SUCCESS)
                if (revertIfNeed()) return false
                if (finishIfNeed()) return true
                throw IllegalStateException("waaaa")
            } else {
                if (revertIfNeed()) return false
                if (finishIfNeed()) return true
                throw IllegalStateException("waaaa")
            }
        }

        private fun addDescriptor(index: Int, expected: E): Boolean {
            while (true) {
                val value = array[index].value
                if (value is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
                    if (value != this) {
                        value.apply()
                    } else {
                        return true
                    }
                } else {
                    return array[index].compareAndSet(expected, this)
                }
            }
        }

        fun read(index: Int): E {
            val status = status.value
            return if (status in listOf(UNDECIDED, FAILED)) {
                if (index == index1) expected1 else expected2
            } else {
                if (index == index1) update1 else update2
            }
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}