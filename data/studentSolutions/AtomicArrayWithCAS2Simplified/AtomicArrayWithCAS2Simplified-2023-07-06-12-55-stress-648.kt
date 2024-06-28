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
        return when (value) {
            is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor -> {
                when (value.status.value) {
                    UNDECIDED, FAILED -> {
                        if (index == value.index1) {
                            value.expected1 as E
                        } else {
                            value.expected2 as E
                        }
                    }
                    else -> {
                        if (index == value.index1) {
                            value.update1 as E
                        } else {
                            value.update2 as E
                        }
                    }
                }
            }
            else -> value as E
        }
    }

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }

        //reorder
        val desc = if (index1 <= index2) {
            CAS2Descriptor(index1, expected1, update1, index2, expected2, update2)
        } else {
            CAS2Descriptor(index2, expected2, update2, index1, expected1, update1)
        }
        desc.apply()
        return desc.status.value == SUCCESS
    }

    inner class CAS2Descriptor(
        val index1: Int,
        val expected1: E,
        val update1: E,
        private val index2: Int,
        val expected2: E,
        val update2: E
    ) {
        val status = atomic(UNDECIDED)

        fun apply() {
            val val1 = array[index1].value
            if (val1 == this) {
                val val2 = array[index2].value
                if (val2 == this) {
                    status.compareAndSet(UNDECIDED, SUCCESS)
                    if (array[index1].compareAndSet(this, update1)) {
                        if (array[index2].compareAndSet(this, update2)) {
                            return
                        }
                        array[index1].compareAndSet(update1, expected1)
                        return
                    }
                    array[index2].compareAndSet(this, expected2)
                }

                if (val2 is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
                    val2.apply()
                    if (val2.status.value != SUCCESS) {
                        status.compareAndSet(UNDECIDED, FAILED)
                        return
                    }
                }

                if (array[index2].compareAndSet(expected2, this)) {
                    status.compareAndSet(UNDECIDED, SUCCESS)
                    if (array[index1].compareAndSet(this, update1)) {
                        if (array[index2].compareAndSet(this, update2)) {
                            return
                        }
                        array[index1].compareAndSet(update1, expected1)
                        return
                    }
                    array[index2].compareAndSet(this, expected2)
                }
                array[index1].compareAndSet(this, expected1)
            } else if (val1 is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
                val1.apply()
                if (val1.status.value != SUCCESS) {
                    status.compareAndSet(UNDECIDED, FAILED)
                    return
                }
            }

            if (array[index1].compareAndSet(expected1, this)) {
                val val2 = array[index2].value
                if (val2 == this) {
                    status.compareAndSet(UNDECIDED, SUCCESS)
                    if (array[index1].compareAndSet(this, update1)) {
                        if (array[index2].compareAndSet(this, update2)) {
                            return
                        }
                        array[index1].compareAndSet(update1, expected1)
                        return
                    }
                    array[index2].compareAndSet(this, expected2)
                } else if (val2 is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
                    val2.apply()
                    if (val2.status.value != SUCCESS) {
                        status.compareAndSet(UNDECIDED, FAILED)
                        return
                    }
                }

                if (array[index2].compareAndSet(expected2, this)) {
                    status.compareAndSet(UNDECIDED, SUCCESS)
                    if (array[index1].compareAndSet(this, update1)) {
                        if (array[index2].compareAndSet(this, update2)) {
                            return
                        }
                        array[index1].compareAndSet(update1, expected1)
                        return
                    }
                    array[index2].compareAndSet(this, expected2)
                }
                array[index1].compareAndSet(this, expected1)
            }
            status.compareAndSet(UNDECIDED, FAILED)
            return
        }

        fun phys(): Boolean {
            if (array[index1].compareAndSet(this, update1)) {
                if (array[index2].compareAndSet(this, update2)) {
                    return true
                }
                array[index1].compareAndSet(update1, expected1)
                return true
            }
            array[index2].compareAndSet(this, expected2)
            return false
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}