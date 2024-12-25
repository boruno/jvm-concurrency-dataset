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
        if (value is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
            if (value.index1 == index && value.status.value != AtomicArrayWithCAS2Simplified.Status.SUCCESS) return value.expected1 as E
            if (value.index1 == index && value.status.value == AtomicArrayWithCAS2Simplified.Status.SUCCESS) return value.update1 as E
            if (value.index2 == index && value.status.value != AtomicArrayWithCAS2Simplified.Status.SUCCESS) return value.expected2 as E
            if (value.index2 == index && value.status.value == AtomicArrayWithCAS2Simplified.Status.SUCCESS) return value.update2 as E
        }
        return value as E
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

    fun dcss(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
        // TODO This implementation is not linearizable!
        // TODO Store a DCSS descriptor in array[index1].
        val dcssDescriptor = DCSSDescriptor(index1, expected1, update1, index2, expected2)
        dcssDescriptor.apply()
        return dcssDescriptor.status.value === Status.SUCCESS
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
                val result = tryToSetValue(index1, expected1) && tryToSetValue(index2, expected2)
                if (result) {
                    status.compareAndSet(Status.UNDECIDED, Status.SUCCESS)
                } else {
                    status.compareAndSet(Status.UNDECIDED, Status.FAILED)
                }
            }
            updateValues()
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

        private fun tryToSetValue(index: Int, expected: E): Boolean {
            while (!array[index].compareAndSet(expected, this)) {
                val currentState: Any? = array[index].value
                if (currentState is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
                    if (currentState === this) return true
                    currentState.apply()
                    // The problem was here
                } else if (currentState !== expected) {
                    return false
                }
            }
            return true
        }
    }

    inner class DCSSDescriptor(
        val index1: Int,
        val expected1: E,
        val update1: E,
        val index2: Int,
        val expected2: E
    ) {
        val status = atomic(Status.UNDECIDED)

        fun apply() {
            if (status.value === Status.UNDECIDED) {
                if (!installDescriptor()) {
                    status.compareAndSet(Status.UNDECIDED, Status.FAILED)
                } else if (status.value === Status.UNDECIDED) {
                    val expectedValue = getExpectedValue()
                    if (expectedValue == expected2) {
                        status.compareAndSet(Status.UNDECIDED, Status.SUCCESS)
                    } else {
                        status.compareAndSet(Status.UNDECIDED, Status.FAILED)
                    }
                }
            }
            updateValues()
        }

        fun justHelp() {
            if (status.value === Status.UNDECIDED) {
                val expectedValue = getExpectedValue()
                if (expectedValue == expected2) {
                    status.compareAndSet(Status.UNDECIDED, Status.SUCCESS)
                } else {
                    status.compareAndSet(Status.UNDECIDED, Status.FAILED)
                }
            }
            updateValues()
        }

        private fun installDescriptor(): Boolean {
            while (true) {
                val currentState: Any? = array[index1].value
                if (array[index1].compareAndSet(expected1, this)) return true
                if (currentState is AtomicArrayWithDCSS<*>.DCSSDescriptor) {
                    if (currentState === this) return true
                    currentState.justHelp()
                    // The problem was here
                } else if (currentState !== expected1) {
                    return false
                }
            }
        }

        private fun getExpectedValue(): E {
            while (true) {
                val currentState = array[index2].value
                when {
                    currentState is AtomicArrayWithCAS2<*>.DCSSDescriptor -> {
                        if (currentState.index2 == index2) return currentState.expected2 as E
                        return if (currentState.status.value === Status.SUCCESS) {
                            currentState.update1 as E
                        } else {
                            currentState.expected1 as E
                        }
                    }
                    else -> {
                        return currentState as E
                    }
                }
            }
        }

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