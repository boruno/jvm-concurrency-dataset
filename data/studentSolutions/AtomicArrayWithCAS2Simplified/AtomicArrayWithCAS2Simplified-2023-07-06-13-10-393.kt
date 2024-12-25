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
        val value = array[index].value
        if (value is AtomicArrayWithCAS2SingleWriter<*>.CAS2Descriptor) {
            return value.get(index) as E
        }
        return value as E
    }

    fun cas2(
            index1: Int, expected1: E, update1: E,
            index2: Int, expected2: E, update2: E
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }

        if (index1 > index2) {
            return cas2(index2, expected2, update2, index1, expected1, update1)
        }
        // TODO: this implementation is not linearizable,
        // TODO: use CAS2Descriptor to fix it.
        val descriptor = CAS2Descriptor(index1, expected1, update1, index2, expected2, update2)
        return descriptor.apply()
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

        // Should be called if the descriptor is set in the first cell
        fun finishUp(): Boolean {
            when (status.value) {
                SUCCESS -> {
                    // both cells have this descriptor, trying to update the values
                    array[index1].compareAndSet(this, update1)
                    array[index2].compareAndSet(this, update2)
                    return true
                }

                FAILED -> {
                    // second cell failed with CAS, undo the first cell
                    array[index1].compareAndSet(this, expected1)
                    return false
                }

                UNDECIDED -> {
                    // check the value in the second cell
                    val value2 = array[index2].value
                    // trying to finish up if there is a descriptor too
                    if (value2 is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
                        if (value2 == this) {
                            // if it's the same descriptor, SUCCESS needs to be set
                            status.compareAndSet(UNDECIDED, SUCCESS)
                            // finish up after setting the correct status
                            return finishUp()
                        }
                        // if it's a different descriptor, let's first finish it up
                        value2.finishUp()
                        // and then try again with this one
                        return finishUp()
                    }

                    // here a descriptor could appear
                    if (array[index2].compareAndSet(this, this)) {
                        status.compareAndSet(UNDECIDED, SUCCESS)
                        // finish up after setting the correct status
                        return finishUp()
                    }
                    
                    // if there is no descriptor, trying to set our descriptor
                    if (array[index2].compareAndSet(expected2, this)) {
                        return finishUp()
                    }
                    status.compareAndSet(UNDECIDED, FAILED)
                    return finishUp()
                }
            }
        }


        fun apply(): Boolean {
            // TODO: install the descriptor, update the status, update the cells.
            val value = array[index1].value
            if (value is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
                if (value != this) {
                    value.finishUp()
                }
            }

            if (array[index1].compareAndSet(expected1, this)) {
                return finishUp()
            }
            return false
        }

        fun get(index: Int): E {
            return if (status.value == SUCCESS) {
                getUpdate(index)
            } else {
                getExpected(index)
            }
        }

        fun getUpdate(index: Int): E {
            return when (index) {
                index1 -> update1
                index2 -> update2
                else -> error("No such index in this descriptor")
            }
        }

        fun getExpected(index: Int): E {
            return when (index) {
                index1 -> expected1
                index2 -> expected2
                else -> error("No such index in this descriptor")
            }
        }

        fun anotherIdx(index: Int): Int {
            return when (index) {
                index1 -> index2
                index2 -> index1
                else -> error("No such index in this descriptor")
            }
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}