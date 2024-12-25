//package day3

import kotlinx.atomicfu.*
import kotlin.math.exp

// This implementation never stores `null` values.
class AtomicArrayWithDCSS<E : Any>(size: Int, initialValue: E) {
    private val array = atomicArrayOfNulls<Any?>(size)

    init {
        // Fill array with the initial value.
        for (i in 0 until size) {
            array[i].value = initialValue
        }
    }

    fun get(index: Int): E? {
        // TODO: the cell can store a descriptor
        val cellValue = array[index].value
        if(cellValue is AtomicArrayWithDCSS<*>.DCSSDescriptor) {
            if(cellValue.state.value == DCSSState.UNDEFINED || cellValue.state.value == DCSSState.FAILED) {
                return cellValue.expected1 as E
            } else {
                return cellValue.update1 as E
            }
        }
        return cellValue as E?
    }

    fun cas(index: Int, expected: E?, update: E?): Boolean {
        // TODO: the cell can store a descriptor
        while(true) {
            if(!array[index].compareAndSet(expected, update)) {
                val cellValue = array[index].value
                if(cellValue is AtomicArrayWithDCSS<*>.DCSSDescriptor) {
                    cellValue.complete()
                    continue
                }

                if(cellValue == expected) {
                    continue
                }
                return false
            }
        }
    }

    fun dcss(
        index1: Int, expected1: E?, update1: E?,
        index2: Int, expected2: E?
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
        // TODO This implementation is not linearizable!
        // TODO Store a DCSS descriptor in array[index1].

        val descriptor = DCSSDescriptor(index1, expected1, update1, index2, expected2)

        while (true) {
            val installed = array[index1].compareAndSet(expected1, descriptor)
            if (!installed) {
                val unexpectedValue = array[index1].value
                if (unexpectedValue is AtomicArrayWithDCSS<*>.DCSSDescriptor) {
                    unexpectedValue.complete()
                    continue
                }

                if (unexpectedValue == expected1) {
                    continue
                }

                return false
            }

            descriptor.complete()
            return descriptor.state.value == DCSSState.SUCCESS
        }
    }

    internal inner class DCSSDescriptor(
        val index1: Int,
        val expected1: E?,
        val update1: E?,
        val index2: Int,
        val expected2: E?
    ) {
        val state = atomic(DCSSState.UNDEFINED)

        fun complete() {
            val expectedCellValue = array[index2].value
            val snapshotIsValid = if (expectedCellValue is AtomicArrayWithDCSS<*>.DCSSDescriptor) {
                val installedCellState = expectedCellValue.state.value
                if (installedCellState == DCSSState.UNDEFINED || installedCellState == DCSSState.FAILED) {
                    expectedCellValue.expected1 == expected2
                } else {
                    expectedCellValue.update1 == expected2
                }
            } else {
                expectedCellValue == expected2
            }

            if (snapshotIsValid) {
                state.compareAndSet(DCSSState.UNDEFINED, DCSSState.SUCCESS)
            } else {
                state.compareAndSet(DCSSState.UNDEFINED, DCSSState.FAILED)
            }

            val putBackValue = if (state.value == DCSSState.SUCCESS) update1 else expected1
            array[index1].compareAndSet(this, putBackValue)
        }
    }

}

internal enum class DCSSState {
    UNDEFINED, FAILED, SUCCESS
}