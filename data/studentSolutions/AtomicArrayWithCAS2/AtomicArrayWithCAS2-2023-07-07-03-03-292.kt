@file:Suppress("DuplicatedCode")

//package day3

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
        while (true) {
            val state = array[index].value
            when {
                (state is AtomicArrayWithCAS2<*>.DCSSDescriptor) -> {
                    state.apply()
                }
                (state is AtomicArrayWithCAS2<*>.CAS2Descriptor) -> {
                    state.apply()
                }
                else -> {
                    @Suppress("UNCHECKED_CAST")
                    return state as E?
                }
            }
        }


    }

    fun cas(index: Int, expected: Any?, update: Any?): Boolean {
        while (true) {
            val state = array[index].value
            when {
                (state is AtomicArrayWithCAS2<*>.DCSSDescriptor) -> {
                    state.apply()
                }
                (state is AtomicArrayWithCAS2<*>.CAS2Descriptor) -> {
                    state.apply()
                }
                (state === expected) -> {
                    if (array[index].compareAndSet(expected, update)) {
                        return true
                    }
                }
                else -> {
                    return false
                }
            }
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
            CAS2Descriptor(index1, expected1, update1, index2, expected2, update2)
        } else {
            CAS2Descriptor(index2, expected2, update2, index1, expected1, update1)
        }
        descriptor.apply()
        return descriptor.isStatusSuccess()
    }

    inner class CAS2Descriptor(
        private val index1: Int,
        private val expected1: E?,
        private val update1: E?,
        private val index2: Int,
        private val expected2: E?,
        private val update2: E?
    ) {
        val status = atomic(Status.UNDECIDED)
        private val descriptor1 = DCSSDescriptor(index1, expected1, this)
        private val descriptor2 = DCSSDescriptor(index2, expected2, this)

        fun setStatusToFailed(): Boolean {
            return status.compareAndSet(Status.UNDECIDED, Status.FAILED)
        }

        fun isStatusSuccess(): Boolean { return status.value === Status.SUCCESS}

        fun isStatusUndecided(): Boolean { return status.value === Status.UNDECIDED}

        private fun installDescriptor(descriptor: DCSSDescriptor): Boolean {
            while (true) {
                val state = array[descriptor.index1].value
                when {
                    (state === this) -> {
                        return true
                    }
                    (state is AtomicArrayWithCAS2<*>.CAS2Descriptor) -> {
                        state.apply()
                        continue
                    }
                    (state is AtomicArrayWithCAS2<*>.DCSSDescriptor) -> {
                        state.apply()
                        continue
                    }
                    (state === descriptor.expected1) -> {
                        if (!isStatusUndecided()) {
                            return isStatusSuccess()
                        }
                        if (descriptor.tryInstall()) {
                            descriptor.apply()
                            return descriptor.isStatusSuccess()
                        } else {
                            return false
                        }
                    }
                    else -> {
                        return false
                    }
                }
            }
        }

        private fun tryInstallDescriptors() {
            if (installDescriptor(descriptor1) && installDescriptor(descriptor2)) {
                status.compareAndSet(
                    Status.UNDECIDED,
                    Status.SUCCESS
                )
            } else {
                setStatusToFailed()
            }
        }

        private fun updateValues() {
            require(status.value !== Status.UNDECIDED)
            if (status.value === Status.SUCCESS) {
                array[index1].compareAndSet(this, update1)
                array[index2].compareAndSet(this, update2)
            } else {
                array[index1].compareAndSet(this, expected1)
                array[index2].compareAndSet(this, expected2)
            }
        }

        fun apply() {
            if (status.value === Status.UNDECIDED) {
                tryInstallDescriptors()
            }
            updateValues()
        }
    }


    inner class DCSSDescriptor(
        val index1: Int,
        val expected1: E?,
        val cas2obj: CAS2Descriptor
    ) {
        val status = atomic(Status.UNDECIDED)

        private fun setStatusToFailed(): Boolean {
            return status.compareAndSet(Status.UNDECIDED, Status.FAILED)
        }

        private fun setStatusToSuccess(): Boolean {
            return status.compareAndSet(Status.UNDECIDED, Status.SUCCESS)
        }

        private fun isSecondConditionTrue(): Boolean {
            return cas2obj.status.value === Status.UNDECIDED
        }

        private fun isStatusUndecided(): Boolean {
            return status.value === Status.UNDECIDED
        }
        fun isStatusSuccess(): Boolean { return status.value === Status.SUCCESS}

        fun tryInstall(): Boolean {
            while (true) {
                val cellState = array[index1].value
                if (!isStatusUndecided()) break
                if (!isSecondConditionTrue()) break

                when {
                    (cellState === this) -> {
                        return true
                    }
                    (cellState === expected1) -> {
                        if (array[index1].compareAndSet(expected1, this)) {
                            return true
                        }
                        break
                    }
                    (cellState is AtomicArrayWithCAS2<*>.DCSSDescriptor) -> {
                        cellState.apply()
                    }
                    (cellState is AtomicArrayWithCAS2<*>.CAS2Descriptor) -> {
                        cellState.apply()
                    }
                    else -> {
                        break
                    }
                }
            }
            if (array[index1].value === this) {
                return true
            }
            setStatusToFailed()
            return false
        }
        fun apply() {
            if (status.value === Status.UNDECIDED) {
                if (isSecondConditionTrue()) {
                    setStatusToSuccess()
                } else {
                    setStatusToFailed()
                }
            }

            require(status.value !== Status.UNDECIDED)

            if (status.value === Status.SUCCESS) {
                array[index1].compareAndSet(this, cas2obj)
            } else {
                array[index1].compareAndSet(this, expected1)
            }
        }

    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}