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
        val element = array[index].value
        return when (element) {
            is AtomicArrayWithCAS2SingleWriter<*>.CAS2Descriptor -> element.get(index) as E
            else -> element as E
        }
    }

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }

        val descriptor = if (index1 < index2) {
            CAS2Descriptor(
                index1 = index1, expected1 = expected1, update1 = update1,
                index2 = index2, expected2 = expected2, update2 = update2
            )
        } else {
            CAS2Descriptor(
                index1 = index2, expected1 = expected2, update1 = update2,
                index2 = index1, expected2 = expected1, update2 = update1
            )
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
            val i1 = index1
            val i2 = index2
            val cell1 = array[i1]
            val cell2 = array[i2]

            val cell1Value = cell1.value
            if (cell1Value is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
                cell1Value.apply()
                return
            }

            val cell2Value = cell2.value
            if (cell2Value is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
                cell2Value.apply()
                return
            }

            val currentStatus = status.value
            when (currentStatus) {
                UNDECIDED -> {
                    var wasFailed = false
                    if (!cell1.compareAndSet(expected1, this)) {
                        status.compareAndSet(UNDECIDED, FAILED)
                        wasFailed = true
                    }

                    if (!cell2.compareAndSet(expected2, this)) {
                        status.compareAndSet(UNDECIDED, FAILED)
                        wasFailed = true
                    }

                    if (wasFailed) {
                        cell1.compareAndSet(this, expected1)
                        cell2.compareAndSet(this, expected2)
                        return
                    }

                    if (status.compareAndSet(UNDECIDED, SUCCESS)) {
                        cell1.compareAndSet(this, update1)
                        cell2.compareAndSet(this, update2)
                        return
                    }
                }

                FAILED -> {
                    cell1.compareAndSet(this, expected1)
                    cell2.compareAndSet(this, expected2)
                }

                SUCCESS -> {
                    cell1.compareAndSet(this, update1)
                    cell2.compareAndSet(this, update2)
                }
                else -> error("wtf")
            }
            // TODO: Install the descriptor, update the status, and update the cells;
            // TODO: create functions for each of these three phases.
        }
    }

    /**
     * UNDECIDED -> A_SUCCESS -> SUCCESS -> UpdatedValue
     *    |          |
     *    |         B_FAILED
     *    |         |
     *    |        \/
     *    --> A_FAILED -> FAILED -> ExpectedValue
     */

    enum class Status {
        UNDECIDED, SUCCESS, FAILED,
        A_SUCCESS, A_FAILED
    }
}