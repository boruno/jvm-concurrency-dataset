@file:Suppress("DuplicatedCode")

package day3

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls

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
        val currentValue = array[index].value
        if (currentValue is AtomicArrayWithCAS2<*>.CAS2Descriptor) {
            return currentValue.valueForIndex(index) as E
        }

        if (currentValue is AtomicArrayWithCAS2<*>.DCSSDescriptor) {
            return currentValue.getValue(index) as E
        }

        return currentValue as E
    }

    fun cas(index: Int, expected: E?, update: E?): Boolean {
        // TODO: the cell can store a descriptor
        while (true) {
            if (!array[index].compareAndSet(expected, update)) {
                val cellValue = array[index].value
                if (cellValue is AtomicArrayWithCAS2<*>.DCSSDescriptor) {
                    cellValue.complete()
                    continue
                }

                if (cellValue is AtomicArrayWithCAS2<*>.CAS2Descriptor) {
                    cellValue.apply()
                    continue
                }

                if (cellValue == expected) {
                    continue
                }
                return false
            }
            return true
        }
    }

    fun dcss(
        index: Int,
        expectedValue: E?,
        descriptorToInstall: CAS2Descriptor,
    ): Boolean {
        // TODO This implementation is not linearizable!
        // TODO Store a DCSS descriptor in array[index1].
        val descriptor = DCSSDescriptor(index, expectedValue, descriptorToInstall)

        while (true) {
            val installed = array[index].compareAndSet(expectedValue, descriptor)
            if (!installed) {
                val unexpectedValue = array[index].value
                if (unexpectedValue is AtomicArrayWithCAS2<*>.DCSSDescriptor) {
                    unexpectedValue.complete()
                    continue
                }

                if (unexpectedValue is AtomicArrayWithCAS2<*>.CAS2Descriptor) {
                    if(unexpectedValue == descriptorToInstall) {
                        return true
                    }
                    unexpectedValue.apply()
                    continue
                }

                if (unexpectedValue == expectedValue) {
                    continue
                }

                return false
            }

            descriptor.complete()
            return descriptor.state.value == Status.SUCCESS
        }
    }

    fun cas2(
        index1: Int, expected1: E?, update1: E?,
        index2: Int, expected2: E?, update2: E?
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
        // TODO: this implementation is not linearizable,
        // TODO: Store a CAS2 descriptor in array[index1].
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
        return descriptor.status.value === Status.SUCCESS
    }

    inner class CAS2Descriptor(
        public val index1: Int,
        public val expected1: E,
        public val update1: E,
        public val index2: Int,
        public val expected2: E,
        public val update2: E
    ) {
        val status = atomic(Status.UNDECIDED)

        fun apply() {
            // TODO: Install the descriptor, update the status, and update the cells;
            // TODO: create functions for each of these three phases.
            val installed = installDescriptors()
            val completeResult = completeDescriptor(installed)
            removeDescriptors(completeResult)
        }

        private fun installDescriptors(): Boolean {
            while (true) {
                if (!array[index1].compareAndSet(expected1, this)) {
                    val currentLeft = array[index1].value

                    if (currentLeft == expected1) {
                        continue
                    }

                    if (currentLeft != this) {
                        if (status.value != Status.UNDECIDED) {
                            return false
                        }

                        if (currentLeft is AtomicArrayWithCAS2<*>.DCSSDescriptor) {
                            currentLeft.complete()
                            continue
                        }

                        if (currentLeft is AtomicArrayWithCAS2<*>.CAS2Descriptor) {
                            currentLeft.apply()
                            continue
                        }

                        return false
                    }
                }

                if (!dcss(index2, expected2, this)) {
                    val currentRight = array[index2].value

                    if (currentRight == expected2) {
                        continue
                    }

                    if (currentRight != this) {
                        if (status.value != Status.UNDECIDED) {
                            return false
                        }

                        if (currentRight is AtomicArrayWithCAS2<*>.DCSSDescriptor) {
                            currentRight.complete()
                            continue
                        }

                        if (currentRight is AtomicArrayWithCAS2<*>.CAS2Descriptor) {
                            currentRight.apply()
                            continue
                        }
                        return false
                    }
                }
                return true
            }
        }

        private fun completeDescriptor(success: Boolean): Status {
            val targetStatus = if (success) Status.SUCCESS else Status.FAILED
            return if (status.compareAndSet(Status.UNDECIDED, targetStatus)) {
                targetStatus
            } else {
                status.value
            }
        }

        private fun removeDescriptors(status: Status) {
            if (status == Status.SUCCESS) {
                array[index1].compareAndSet(this, update1)
                array[index2].compareAndSet(this, update2)
            } else if (status == Status.FAILED) {
                array[index1].compareAndSet(this, expected1)
                array[index2].compareAndSet(this, expected2)
            } else {
                error("illegal state")
            }
        }

        fun valueForIndex(index: Int): E {
            val currentState = status.value
            if (currentState == Status.UNDECIDED || currentState == Status.FAILED) {
                return if (index1 == index) {
                    expected1
                } else {
                    expected2
                }
            }

            if (currentState == Status.SUCCESS) {
                return if (index1 == index) {
                    update1
                } else {
                    update2
                }
            }

            error("unexpected state")
        }
    }

    internal inner class DCSSDescriptor(
        val index: Int,
        val expected: E?,
        val descriptor: CAS2Descriptor
    ) {
        val state = atomic(Status.UNDECIDED)

        fun complete() {
            val descriptorValue = descriptor.status.value
            val snapshotIsValid = descriptorValue == Status.UNDECIDED

            if (snapshotIsValid) {
                state.compareAndSet(Status.UNDECIDED, Status.SUCCESS)
            } else {
                state.compareAndSet(Status.UNDECIDED, Status.SUCCESS)
            }

            val putBackValue = if (state.value == Status.SUCCESS) descriptor else expected
            array[index].compareAndSet(this, putBackValue)
        }

        fun getValue(index: Int): E {
            val currentState = state.value
            if (currentState == Status.UNDECIDED || currentState == Status.FAILED) {
                return expected!!
            }

            if (currentState == Status.SUCCESS) {
                return descriptor.valueForIndex(index)
            }

            error("illegal state exc")
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}