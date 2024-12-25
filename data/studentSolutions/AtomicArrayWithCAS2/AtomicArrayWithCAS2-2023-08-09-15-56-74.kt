@file:Suppress("DuplicatedCode", "UNCHECKED_CAST")

//package day3

import day3.AtomicArrayWithCAS2.Status.*
import java.util.concurrent.atomic.*

// This implementation never stores `null` values.
class AtomicArrayWithCAS2<E : Any>(size: Int, initialValue: E) {
    private val array = AtomicReferenceArray<Any?>(size)

    init {
        // Fill array with the initial value.
        for (i in 0 until size) {
            array[i] = initialValue
        }
    }

    fun get(index: Int): E? {
        // TODO: the cell can store a descriptor
        val value = array[index]
        if (value is AtomicArrayWithCAS2<*>.CAS2Descriptor) {
            return value.getValue(index) as E
        } else if (value is AtomicArrayWithCAS2<*>.DCSSDescriptor) {
            return value.getValue() as E
        } else {
            return value as E
        }
    }

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
        // TODO: this implementation is not linearizable,
        // TODO: Store a CAS2 descriptor in array[index1].
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
        return descriptor.status.get() === SUCCESS
    }

    fun dcss(
        index: Int,
        expectedCellState: Any?,
        updateCellState: Any?,
        statusReference: AtomicReference<*>
    ) : Boolean {
        val dcssDescriptor = DCSSDescriptor(index, expectedCellState, updateCellState, statusReference)
        dcssDescriptor.apply()
        return dcssDescriptor.status.get() == SUCCESS
    }
    inner class DCSSDescriptor(
        val index: Int,
        val expectedCellState: Any?,
        val updateCellState: Any?,
        val statusReference: AtomicReference<*>,
    ) {
        val status = AtomicReference(UNDECIDED)
        fun apply() {
            if (installDescriptor() && statusReference.get() == UNDECIDED){
                status.compareAndSet(UNDECIDED, SUCCESS)
            }
            finish()
        }

        private fun finish() {
            if (status.get() == SUCCESS) {
                array.compareAndSet(index, this, updateCellState)
            } else if (status.get() == FAILED) {
                array.compareAndSet(index, this, expectedCellState)
            }
        }

        private fun installDescriptor() : Boolean{
            while (true) {
                if (status.get() != UNDECIDED){
                    return false
                }
                val curValue = array[index]
                if (curValue == this) {
                    return true
                } else if (curValue is AtomicArrayWithCAS2<*>.DCSSDescriptor) {
                    curValue.apply()
                } else if (curValue == expectedCellState) {
                    return true
                } else if (curValue is AtomicArrayWithCAS2<*>.CAS2Descriptor) {
                    curValue.apply()
                } else {
                    return false
                }
            }
        }

        fun getValue() : Any {
            if (status.get() != SUCCESS) {
                return expectedCellState as E
            } else {
                return (updateCellState as AtomicArrayWithCAS2<*>.CAS2Descriptor).getValue(index)
            }
        }
    }

    inner class CAS2Descriptor(
        val index1: Int,
        val expected1: E,
        val update1: E,
        val index2: Int,
        val expected2: E,
        val update2: E
    ) {
        val status = AtomicReference(UNDECIDED)

        fun apply() {
            if (installDescriptor(index1) && installDescriptor(index2)) {
                status.compareAndSet(UNDECIDED, SUCCESS )
            } else {
                status.compareAndSet(UNDECIDED, FAILED)
            }
            finish()
        }

        private fun installDescriptor(idx: Int) : Boolean {
            val expectedValue = getExpectedValue(idx)
            while (true) {
                if (status.get() != UNDECIDED) {
                    return false
                }
                val curValue = array[idx]
                if (curValue == this) {
                    return true
                }
                if (curValue is AtomicArrayWithCAS2<*>.CAS2Descriptor) {
                    if (curValue == this) {
                        return true
                    } else {
                        curValue.apply()
                    }
                } else if (curValue is AtomicArrayWithCAS2<*>.DCSSDescriptor) {
                    curValue.apply()
                } else {
                    if (curValue == expectedValue) {
                        if (dcss(idx, expectedValue, this, status)) {
                            return true
                        }
                    } else {
                        return false
                    }
                }
            }
        }
        private fun finish(){
            if (status.get() == SUCCESS){
                array.compareAndSet(index1, this, getUpdatedValue(index1))
                array.compareAndSet(index2, this, getUpdatedValue(index2))
            } else if (status.get() == FAILED) {
                array.compareAndSet(index1, this, getExpectedValue(index1))
                array.compareAndSet(index2, this, getExpectedValue(index2))
            }
        }

        fun getExpectedValue(i: Int) : E {
            if (i == index1) {
                return expected1
            } else {
                return expected2
            }
        }
        fun getUpdatedValue(i: Int) : E {
            if (i == index1) {
                return update1
            } else {
                return update2
            }
        }
        fun getValue(i: Int) : E {
            return if (status.get() != SUCCESS) {
                getExpectedValue(i)
            } else {
                getUpdatedValue(i)
            }
        }

    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}