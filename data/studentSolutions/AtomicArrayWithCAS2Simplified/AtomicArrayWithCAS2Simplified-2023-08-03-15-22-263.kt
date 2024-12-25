//package day3

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
        val item = array[index].value
        if (item is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {

            if (item.status.value == SUCCESS) {
                if (index == item.index1) return item.update1 as E
                if (index == item.index2) return item.update2 as E
            }
            else if(item.status.value == FAILED) {
                if (index == item.index1) return item.expected1 as E
                if (index == item.index2) return item.expected2 as E
            }
        }
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
        val index1: Int,
        val expected1: E,
        val update1: E,
        val index2: Int,
        val expected2: E,
        val update2: E
    ) {
        val status = atomic(UNDECIDED)

        fun apply() {

            val idx1: Int
            val exp1: E
            val upd1: E
            val idx2: Int
            val exp2: E
            val upd2: E
            if (index1 <= index2) {
                idx1 = index1
                exp1 = expected1
                idx2 = index2
                exp2 = expected2
            } else {
                idx1 = index2
                exp1 = expected2
                idx2 = index1
                exp2 = expected1
            }

            while (!array[idx1].compareAndSet(expected1, this)) {
                val curValue1 = array[idx1].value
                //if the value is a descriptor
                if (curValue1 is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
                    if (curValue1 == this) {
                        break
                    }
                    if (curValue1.status.value == UNDECIDED)
                        curValue1.apply()
                    else {
                        curValue1.applyValues()
                    }
                    continue
                }
                if (curValue1 != exp1) {
                    this.status.compareAndSet(UNDECIDED, FAILED)
                    break
                }
            }
            while (!array[idx2].compareAndSet(expected2, this)) {
                val curValue2 = array[idx2].value
                //if the value is a descriptor
                if (curValue2 is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
                    if (curValue2 == this) {
                        break
                    }
                    if (curValue2.status.value == UNDECIDED)
                        curValue2.apply()
                    else
                        curValue2.applyValues()
                    continue
                }
                if (curValue2 != exp2) {
                    this.status.compareAndSet(UNDECIDED, FAILED)
                    break
                }

            }
            this.status.compareAndSet(UNDECIDED, SUCCESS)
            this.applyValues()
            // TODO: Install the descriptor, update the status, and update the cells;
            // TODO: create functions for each of these three phases.
        }

        private fun applyValues() {
            if (this.status.value == SUCCESS) {
                array[index1].compareAndSet(this, update1)
                array[index2].compareAndSet(this, update2)
            }
            if (this.status.value == FAILED) {
                array[index1].compareAndSet(this, expected1)
                array[index2].compareAndSet(this, expected2)
            }
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}