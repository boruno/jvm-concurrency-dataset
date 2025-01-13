//package day3

import AtomicArrayWithCAS2SingleWriter.Status.*
import kotlinx.atomicfu.*

// This implementation never stores `null` values.
class AtomicArrayWithCAS2SingleWriter<E : Any>(size: Int, initialValue: E) {
    private val array = atomicArrayOfNulls<Any?>(size)

    init {
        // Fill array with the initial value.
        for (i in 0 until size) {
            array[i].value = initialValue
        }
    }

    fun get(index: Int): Any {
        // TODO: the cell can store CAS2Descriptor
        val cellValue = array[index].value
        if (cellValue is AtomicArrayWithCAS2SingleWriter<*>.CAS2Descriptor) {
            return when (cellValue.status.value) {
                SUCCESS -> {
                    if (index == cellValue.index1) {
                        cellValue.update1
                    }
                    else {
                        cellValue.update2
                    }
                }

                UNDECIDED, FAILED -> if (index == cellValue.index1) {
                    cellValue.expected1
                }
                else {
                    cellValue.expected2
                }
            }
        }
        return cellValue as E
    }

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
        // TODO: this implementation is not linearizable,
        // TODO: use CAS2Descriptor to fix it.
        // TODO: Note that only one thread can call CAS2!
        return CAS2Descriptor(index1, expected1, update1, index2, expected2, update2).apply()
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

        fun apply(): Boolean {
            // TODO: install the descriptor, update the status, update the cells.
            val cellOne = array[index1]
            val cellTwo = array[index2]
            return if (setDescriptor(cellOne, expected1) && setDescriptor(cellTwo, expected2)) {
                status.compareAndSet(UNDECIDED, SUCCESS)
                cellOne.compareAndSet(this, update1)
                cellTwo.compareAndSet(this, update2)
                true
            } else {
                status.compareAndSet(UNDECIDED, FAILED)
                cellOne.compareAndSet(this, expected1)
                cellOne.compareAndSet(this, expected2)
                false
            }
        }


        private fun setDescriptor(cellOne: AtomicRef<Any?>, expected: E): Boolean{
            if (cellOne.compareAndSet(expected, this)) return true
            if (status.compareAndSet(UNDECIDED, FAILED)) {
                cellOne.compareAndSet(this, expected)
            }

            return false
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}