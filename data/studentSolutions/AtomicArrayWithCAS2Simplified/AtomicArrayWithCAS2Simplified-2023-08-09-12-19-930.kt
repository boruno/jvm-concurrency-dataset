@file:Suppress("DuplicatedCode", "UNCHECKED_CAST")

//package day3

import day3.AtomicArrayWithCAS2Simplified.Status.*
import java.util.concurrent.atomic.*


// This implementation never stores `null` values.
class AtomicArrayWithCAS2Simplified<E : Any>(size: Int, initialValue: E) {
    private val array = AtomicReferenceArray<Any?>(size)

    init {
        // Fill array with the initial value.
        for (i in 0 until size) {
            array[i] = initialValue
        }
    }

    fun get(index: Int): E {
        val elem = array.get(index)
        if (elem is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
            if (elem.status.get() != SUCCESS) {
                if (elem.index1 == index) return elem.expected1 as E
                return elem.expected2 as E
            }
            if (elem.index1 == index) return elem.update1 as E
            return elem.update2 as E
        }
        return elem as E
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
        return descriptor.status.get() === SUCCESS
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

        fun installDescriptor(idx: Int, exp: Any): Boolean {
            while (true) {
                if (array.compareAndSet(idx, exp, this)) {
                    // we need to continue with ind2
                    return true
                } else {
                    // check whether this is our descriptor
                    val val1 = array.get(idx)
                    if (val1 === this) {
                        // someone installed our descriptior in ind1
                        // we need to continue with ind2
                        return true
                    } else {
                        if (val1 is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
                            // it's a  foreign descriptor, we need to help him

                            // state unknown - need to install in b
                            // state failed - need to remove it and reset the value
                            // state successfull - need to update the value and re-strart us
                            when (val1.status.get()) {
                                UNDECIDED -> {
                                    val foreignBSetup = val1.installDescriptor(val1.index2, val1.expected2)
                                    if (foreignBSetup) {
                                        // we got to restart the loop
                                        val1.updateStatus(SUCCESS)
                                        val1.updateCell()
                                        continue
                                    } else {
                                        // we failed to install it
                                        val1.updateStatus(FAILED)
                                        val1.updateCell()
                                        return false
                                    }
                                }
                                FAILED -> {
                                    val1.updateCell()
                                    continue
                                }
                                SUCCESS -> {
                                    val1.updateCell()
                                    continue
                                }
                                null -> throw Exception("")
                            }
                        } else {
                            // it's an undexpected value, we need to fail
                            return false
                        }
                    }
                }
            }
        }

        fun updateStatus(stat: Status) {
            status.compareAndSet(UNDECIDED, stat)
        }

        fun updateCell() {
            when(status.get()) {
                UNDECIDED, null -> throw Exception("")
                FAILED -> {
                    // roll back the values
                    array.compareAndSet(index1, this, expected1)
                    array.compareAndSet(index2, this, expected2)
                }
                SUCCESS -> {
                    // apply original values
                    array.compareAndSet(index1, this, update1)
                    array.compareAndSet(index2, this, update2)
                }
            }
        }

        fun apply() {
            // TODO: Install the descriptor, update the status, and update the cells;
            // TODO: create functions for each of these three phases.
            var success = false
            val tryA = installDescriptor(index1, expected1)
            if (tryA) {
                val tryB = installDescriptor(index2, expected2)
                if (tryB) {
                    success = true
                    updateStatus(SUCCESS)
                }
            }
            if (!success) {
                updateStatus(FAILED)
            }
            updateCell()
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}