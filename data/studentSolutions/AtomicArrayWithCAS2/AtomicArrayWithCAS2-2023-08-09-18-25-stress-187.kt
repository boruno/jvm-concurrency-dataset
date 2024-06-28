@file:Suppress("DuplicatedCode", "UNCHECKED_CAST")

package day3

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

    fun get(index: Int): E {
        // TODO: the cell can store CAS2Descriptor
        val state = array[index]
        if (state is AtomicArrayWithCAS2<*>.CAS2Descriptor) {
            if (state.status.get() != AtomicArrayWithCAS2.Status.SUCCESS) {
                if (index == state.index1) {
                    return state.expected1 as E
                }
                if (index == state.index2) {
                    return state.expected2 as E
                }
            } else {
                if (index == state.index1) {
                    return state.update1 as E
                }
                if (index == state.index2) {
                    return state.update2 as E
                }
            }
        }

        return state as E
    }

    fun cas2(
        index1: Int, expected1: E?, update1: E?,
        index2: Int, expected2: E?, update2: E?
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
        // TODO: this implementation is not linearizable,
        // TODO: Store a CAS2 descriptor in array[index1].
//        if (array[index1] != expected1 || array[index2] != expected2) return false
//        array[index1] = update1
//        array[index2] = update2
//        return true
        val descriptor = if (index1 < index2) {
            CAS2Descriptor(
                index1 = index1, expected1 = expected1!!, update1 = update1!!,
                index2 = index2, expected2 = expected2!!, update2 = update2!!
            )
        } else {
            CAS2Descriptor(
                index1 = index2, expected1 = expected2!!, update1 = update2!!,
                index2 = index1, expected2 = expected1!!, update2 = update1!!
            )
        }
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

        fun apply() {
            // TODO: Copy the implementation from `AtomicArrayWithCAS2Simplified`
            // TODO: and use `dcss(..)` to install the descriptor.
            installDescriptor()
            updateStatus()
            setValues()
        }

        private fun updateStatus() {
            if (array[index1] == this) {
                if (array[index2] == this) {
                    status.compareAndSet(UNDECIDED, SUCCESS)
                } else {
                    status.compareAndSet(UNDECIDED, FAILED)
                }
            }
            // TEST LINE, TO TRY
            status.compareAndSet(UNDECIDED, FAILED)
        }

        private fun isDescriptor(descr: Any?): Boolean {
            return descr is AtomicArrayWithCAS2<*>.CAS2Descriptor && descr != this
        }

        private fun helpDescriptor(descr: Any?): Boolean {
            if (descr is AtomicArrayWithCAS2<*>.CAS2Descriptor && descr != this) {
                if (descr.status.get() == UNDECIDED)
                    descr.apply()
                else
                    descr.setValues()

                return true
            }
            return false
        }

        private fun installDescriptor() {
            while (true) {
                if (status.get() != UNDECIDED) return
                var aa1 = array[index1]
                if (array[index1] == this || dcss(index1, expected1, this, status, UNDECIDED)) {
                    var aa2 = array[index2]
                    if (array[index2] == this || dcss(index2, expected2, this, status, UNDECIDED)) {
                        return
                    } else {
                        if (helpDescriptor(array[index2]))
                            continue
                        if (!isDescriptor(aa2) && aa2 != expected2 && aa2 == array[index2])
                            return
                    }
                } else {
                    if (helpDescriptor(array[index1]))
                        continue
                    if (!isDescriptor(aa1) && aa1 != expected1 && aa1 == array[index1])
                        return
                }
            }
        }

        private fun setValues() {
            if (status.get() == SUCCESS) {
                array.compareAndSet(index1, this, update1)
                array.compareAndSet(index2, this, update2)

            } else if (status.get() == FAILED) {
                array.compareAndSet(index1, this, expected1)
                array.compareAndSet(index2, this, expected2)
            }
        }
    }


    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }

    // TODO: Please use this DCSS implementation to ensure that
    // TODO: the status is `UNDECIDED` when installing the descriptor.
    fun dcss(
        index: Int,
        expectedCellState: Any?,
        updateCellState: Any?,
        statusReference: AtomicReference<*>,
        expectedStatus: Any?
    ): Boolean =
        if (array[index] == expectedCellState && statusReference.get() == expectedStatus) {
            array[index] = updateCellState
            true
        } else {
            false
        }

}