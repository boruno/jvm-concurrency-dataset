@file:Suppress("DuplicatedCode")

package day3

import kotlinx.atomicfu.*

// This implementation never stores `null` values.
class AtomicArrayWithCAS2<E : Any>(size: Int, initialValue: E) {
    private val array = atomicArrayOfNulls<Any?>(size)

    init {
        // Fill array with the initial value.
        for (i in 0 until size) {
            array[i].value = initialValue
        }
    }

    fun get(index: Int): E? {
        // TODO: the cell can store a descriptor
        // TODO: the cell can store CAS2Descriptor
        val cellValue = array[index].value
        if (cellValue is DCSSDescriptor<*>) {
            val status = cellValue.status.value
            return when (status) {
                Status.SUCCESS -> {
                    cellValue.updateA
                }

                Status.UNDECIDED, Status.FAILED -> cellValue.expectedA
            } as E
        }
        return cellValue as E
    }

    fun cas(index: Int, expected: E?, update: E?): Boolean {
        while (true) {
            val value = array[index].value
            when (value) {
                expected -> {
                    if (array[index].compareAndSet(expected, update)) {
                        return true
                    }
                }

                is DCSSDescriptor<*> -> {
                    value.help()
                }

                else -> {
                    return false
                }
            }
        }

    }

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
        // TODO: this implementation is not linearizable,
        // TODO: Store a CAS2 descriptor in array[index1].

        // install status with dcss for the first cell
        val firstCellDescriptor = MyDCSSDescriptor(index1, expected1, update1, index2, expected2)
        val secondCellDescriptor = MyDCSSDescriptor(index2, expected2, update2, index1, expected1)
        return if (firstCellDescriptor.install() && secondCellDescriptor.install()) {
            firstCellDescriptor.setValue(index1, update1) && secondCellDescriptor.setValue(index2, update2)
        }
        else {
            firstCellDescriptor.setValue(index1, expected1) && secondCellDescriptor.setValue(index2, expected2)
        }
    }


    private inner class MyDCSSDescriptor(
        indexA: Int,
        expectedA: E,
        updateA: E,
        indexB: Int,
        expectedB: E
    ) : DCSSDescriptor<E>(indexA, expectedA, updateA, indexB, expectedB) {

        override fun getArrayValue(index: Int): Any? {
            return array[index].value
        }

        override fun setValue(index: Int, value: E): Boolean {
            return array[index].compareAndSet(this, value)
        }

        override fun setDescriptor(index: Int, expected: E, descriptor: DCSSDescriptor<*>): Boolean {
            return array[index].compareAndSet(expected, descriptor)
        }
    }
}

