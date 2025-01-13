@file:Suppress("DuplicatedCode")

//package day3

import AtomicArrayWithCAS2.Status.*
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

    fun get(index: Int): E {
        // TODO: the cell can store a descriptor
        while (true) {
            val value = array[index].value
            if (value is AtomicArrayWithCAS2<*>.CAS2Descriptor) {
                value.apply()
            } else if (value is AtomicArrayWithCAS2<*>.DCSSDescriptor) {
                value.apply()
/*
                val v = value.getValue()
                if (v is AtomicArrayWithCAS2<*>.CAS2Descriptor) {
                    v.apply()
                } else {
                    return v as E
                }
*/
            } else {
                return value as E
            }
        }
    }

    fun cas(index: Int, expected: E, update: E): Boolean {
        // TODO: the cell can store a descriptor
        while (true) {
            val curState = array[index].value

            when {
                curState is AtomicArrayWithCAS2<*>.CAS2Descriptor -> {
                    curState.apply()
                }

                curState is AtomicArrayWithCAS2<*>.DCSSDescriptor -> {
                    curState.apply()
                }

                curState != expected -> {
                    return false
                }

                else -> {
                    if (array[index].compareAndSet(expected, update)) return true
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
        val descriptor = if (index1 < index2) {
            CAS2Descriptor(index1, expected1, update1, index2, expected2, update2)
        } else {
            CAS2Descriptor(index2, expected2, update2, index1, expected1, update1)
        }
//        if (!descriptor.tryInstallDescriptor()) return false
        descriptor.apply()
        return descriptor.status.value == SUCCESS
    }

    inner class CAS2Descriptor(
        private val index1: Int,
        private val expected1: E,
        private val update1: E,
        val index2: Int,
        val expected2: E,
        private val update2: E
    ) {
        val status = atomic(UNDECIDED)
        val descriptor = DCSSDescriptor(this)

        fun apply() {
            // TODO: install the descriptor, update the status, update the cells.
            if (status.value == UNDECIDED) {
                val success = tryInstallDescriptor()
                val statusValue = if (success) SUCCESS else FAILED
                status.compareAndSet(UNDECIDED, statusValue)
            }

            updateValues()
        }

        fun tryInstallDescriptor(): Boolean {
            return tryInstallDescriptor(index1, expected1, false) &&
                    tryInstallDescriptor(index2, expected2, true)
        }

        private fun tryInstallDescriptor(index: Int, expected: E, useDcss: Boolean): Boolean {
            while (true) {
                val curState = array[index].value
                when {
                    curState === this -> {
                        return true // already installed
                    }

                    curState is AtomicArrayWithCAS2<*>.CAS2Descriptor -> {
                        curState.apply()
                    }

                    curState is AtomicArrayWithCAS2<*>.DCSSDescriptor -> {
                        curState.apply()
                    }

                    curState === expected -> {
                        if (!useDcss) {
                            if (array[index].compareAndSet(expected, this)) {
                                return true // successfully installed
                            } else {
                                continue // retry
                            }
                        } else {
                            val installed = descriptor.tryInstallDescriptorDcss()
                            if (!installed) return false
                            descriptor.apply()
                            if (descriptor.status.value == SUCCESS) {
                                return true
                            } else {
                                continue // retry
                            }
                        }
                    }

                    else -> {
                        return false // value, not expected
                    }
                }
            }
        }

        private fun updateValues() {
            if (status.value == SUCCESS) {
                array[index1].compareAndSet(this, update1)
                array[index2].compareAndSet(this, update2)
            } else {
                array[index1].compareAndSet(this, expected1)
                array[index2].compareAndSet(this, expected2)
            }
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }

    /*    fun dcss(
            index1: Int, expected1: E, update1: CAS2Descriptor
        ): Boolean {
    //        require(index1 != index2) { "The indices should be different" }
            // TODO This implementation is not linearizable!
            // TODO Store a DCSS descriptor in array[index1].
            val descriptor = DCSSDescriptor(index1, expected1, update1)
            val installed = descriptor.tryInstallDescriptor()
            if (!installed) return false
            descriptor.apply()
            return descriptor.status.value == SUCCESS
        }*/

    inner class DCSSDescriptor(
//        private val index1: Int,
//        val expected1: E,
        val cas: CAS2Descriptor,
    ) {
        val status = atomic(UNDECIDED)

        fun apply() {
            // update the status, update the cell
            if (status.value == UNDECIDED) {
                val success = cas.status.value === UNDECIDED
                val statusValue = if (success) SUCCESS else FAILED
                status.compareAndSet(UNDECIDED, statusValue)
            }

            if (status.value == SUCCESS) {
                array[cas.index2].compareAndSet(this, cas)
            } else {
                array[cas.index2].compareAndSet(this, cas.expected2)
            }
        }

        fun tryInstallDescriptorDcss(): Boolean {
            return tryInstallDescriptorDcss(cas.index2, cas.expected2)
        }

        private fun tryInstallDescriptorDcss(index: Int, expected: E): Boolean {
            while (true) {
                val curState = array[index].value
                when {
                    curState === this -> {
                        return true
                    }

                    curState === this.cas -> {
                        return true
                    }

                    curState is AtomicArrayWithCAS2<*>.CAS2Descriptor -> {
                        curState.apply()
                    }

                    curState is AtomicArrayWithDCSS<*>.DCSSDescriptor -> {
                        curState.apply()
                    }

                    curState === expected -> {
//                        if (this.cas.status.value !== UNDECIDED) return false //TODO ???
                        if (array[index].compareAndSet(expected, this)) {
                            return true // successfully installed
                        } else {
                            continue // retry
                        }
                    }

                    else -> {
                        return false // value, not expected
                    }
                }
            }
        }

        fun getValue(): Any {
            if (status.value === SUCCESS) return cas
            return cas.expected2
        }
    }

}