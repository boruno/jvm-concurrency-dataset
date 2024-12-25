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

    fun get(index: Int): E {
        // TODO: the cell can store a descriptor
        val value = array[index].value
        if (value is AtomicArrayWithCAS2<*>.CAS2Descriptor) {
            return getValueForFromCASDescriptor(value, index)
        }
        if (value is AtomicArrayWithCAS2<*>.DCSSDescriptor) {
            if (value.status.value != Status.SUCCESS) {
                return value.expected1 as E
            }
            if (value.status.value == Status.SUCCESS) {
                return getValueForFromCASDescriptor(value.update1, index)
            }
        }
        return value as E
    }

    private fun getValueForFromCASDescriptor(value: AtomicArrayWithCAS2<*>.CAS2Descriptor, index: Int): E {
        return if (value.index1 == index) {
            if (value.status.value != Status.SUCCESS) value.expected1 as E else value.update1 as E
        } else {
            if (value.status.value != Status.SUCCESS) value.expected2 as E else value.update2 as E
        }
    }

    fun cas(index: Int, expected: E?, update: E?): Boolean {
        // TODO: the cell can store a descriptor
        while (true) {
            if (array[index].compareAndSet(expected, update)) return true
            val value = array[index].value
            when {
                value is AtomicArrayWithCAS2<*>.DCSSDescriptor -> {
                    value.justHelp()
                }
                value is AtomicArrayWithCAS2<*>.CAS2Descriptor -> {
                    value.apply()
                }
                value === expected -> {
                    continue
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
        // Make ordering for indexies
        val caS2Descriptor = if (index1 < index2) {
            CAS2Descriptor(index1, expected1, update1, index2, expected2, update2)
        } else {
            CAS2Descriptor(index2, expected2, update2, index1, expected1, update1)
        }
        caS2Descriptor.apply()
        return caS2Descriptor.status.value === Status.SUCCESS
    }

    inner class CAS2Descriptor(
        val index1: Int,
        val expected1: E,
        val update1: E,
        val index2: Int,
        val expected2: E,
        val update2: E
    ) {
        val status = atomic(Status.UNDECIDED)

        fun apply() {
            // TODO: install the descriptor, update the status, update the cells.
            if (status.value === Status.UNDECIDED) {
                val result = tryToSetCAS2DescritporValue(index1, expected1) && tryToSetDCSSDescritporValue(index2, expected2)
                if (result) {
                    status.compareAndSet(Status.UNDECIDED, Status.SUCCESS)
                } else {
                    status.compareAndSet(Status.UNDECIDED, Status.FAILED)
                }
            }
            updateValues()
        }

        private fun tryToSetCAS2DescritporValue(index: Int, expected: E): Boolean {
            while (!array[index].compareAndSet(expected, this)) {
                val currentState: Any? = array[index].value
                if (currentState is AtomicArrayWithCAS2<*>.CAS2Descriptor) {
                    if (currentState === this) return true
                    currentState.apply()
                } else if (currentState is AtomicArrayWithCAS2<*>.DCSSDescriptor) {
                    currentState.justHelp()
                    // The problem was here
                } else if (currentState !== expected) {
                    return false
                }
            }
            return true
        }

        private fun tryToSetDCSSDescritporValue(index: Int, expected: E): Boolean {
            // Store a DCSS descriptor and apply it
            val dcssDescriptor = DCSSDescriptor(index, expected, this)
            while (!array[index].compareAndSet(expected, dcssDescriptor)) {
                val currentState: Any? = array[index].value
                if (currentState is AtomicArrayWithCAS2<*>.CAS2Descriptor) {
                    if (currentState === this) return true
                    currentState.apply()
                    // The problem was here
                } else if (currentState is AtomicArrayWithCAS2<*>.DCSSDescriptor) {
                    if (currentState === dcssDescriptor) return true
                    currentState.justHelp()
                    // The problem was here
                } else if (currentState !== expected) {
                    return false
                }
            }
            dcssDescriptor.apply()
            return dcssDescriptor.status.value === Status.SUCCESS
        }

        private fun updateValues() {
            if (status.value == Status.SUCCESS) {
                array[index1].compareAndSet(this, update1)
                array[index2].compareAndSet(this, update2)
            } else {
                array[index1].compareAndSet(this, expected1)
                array[index2].compareAndSet(this, expected2)
            }
        }
    }

    inner class DCSSDescriptor(
        val index1: Int,
        val expected1: E,
        val update1: CAS2Descriptor
    ) {
        val status = atomic(Status.UNDECIDED)

        fun apply() {
            if (status.value === Status.UNDECIDED) {
                if (installDescriptor() && update1.status.value == Status.UNDECIDED) {
                    status.compareAndSet(Status.UNDECIDED, Status.SUCCESS)
                } else {
                    status.compareAndSet(Status.UNDECIDED, Status.FAILED)
                }
            }
            updateValues()
        }

        fun justHelp() {
            if (status.value === Status.UNDECIDED && update1.status.value == Status.UNDECIDED) {
                status.compareAndSet(Status.UNDECIDED, Status.SUCCESS)
            } else {
                status.compareAndSet(Status.UNDECIDED, Status.FAILED)
            }
            updateValues()
        }

        private fun installDescriptor(): Boolean {
            while (true) {
                val currentState: Any? = array[index1].value
                if (array[index1].compareAndSet(expected1, this)) return true
                if (currentState is AtomicArrayWithCAS2<*>.DCSSDescriptor) {
                    if (currentState === this) return true
                    currentState.justHelp()
                    // The problem was here
                } else if (currentState !== expected1) {
                    return false
                }
            }
        }

//        private fun getExpectedValue(): Status? {
//            while (true) {
//                val currentState = array[index1].value
//                when {
//                    currentState is AtomicArrayWithCAS2<*>.DCSSDescriptor -> {
//                        return currentState.status.value
//                    }
//                    else -> {
//                        return null
//                    }
//                }
//            }
//        }

        private fun updateValues() {
            if (status.value == Status.SUCCESS) {
                array[index1].compareAndSet(this, update1)
            } else {
                array[index1].compareAndSet(this, expected1)
            }
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}