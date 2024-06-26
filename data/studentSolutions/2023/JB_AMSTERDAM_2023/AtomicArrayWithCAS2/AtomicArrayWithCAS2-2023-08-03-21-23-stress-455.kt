@file:Suppress("DuplicatedCode")

package day3

import day3.AtomicArrayWithCAS2.Status.*
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
        val result = when (val value = array[index].value) {
            is AtomicArrayWithCAS2<*>.CAS2Descriptor -> value.getValue(index)
            is AtomicArrayWithCAS2<*>.DCSSDescriptor -> value.getValue()
            else -> value
        } as E?

        return result
    }

    fun cas(index: Int, expected: E?, update: E?): Boolean {
        while (true) {
            when (val value = array[index].value) {

                // expected value, try to set
                expected -> {
                    if (array[index].compareAndSet(expected, update)) {
                        // set successfully
                        return true
                    }
                }

                // contains a descriptor, help
                is AtomicArrayWithCAS2<*>.DCSSDescriptor -> value.help()
                is AtomicArrayWithCAS2<*>.CAS2Descriptor -> value.apply()

                // unexpected value
                else -> return false
            }
        }
    }

    fun cas2(
        index1: Int, expected1: E?, update1: E?,
        index2: Int, expected2: E?, update2: E?
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }

        // keep indexes sorted to avoid deadlocks
        val descriptor = if (index1 < index2) CAS2Descriptor(
            index1 = index1, expected1 = expected1, update1 = update1,
            index2 = index2, expected2 = expected2, update2 = update2
        ) else CAS2Descriptor(
            index1 = index2, expected1 = expected2, update1 = update2,
            index2 = index1, expected2 = expected1, update2 = update1
        )

        descriptor.apply()
        return descriptor.status.value == SUCCESS
    }

    inner class CAS2Descriptor(
        private val index1: Int,
        private val expected1: E?,
        private val update1: E?,
        private val index2: Int,
        private val expected2: E?,
        private val update2: E?
    ) {
        val status = atomic(UNDECIDED)

        private val dcssDesc = DCSSDescriptor(index2, expected2, cas2Desc = this, expectedStatus = UNDECIDED)

        fun getValue(index: Int): E? {
            return if (status.value == SUCCESS) {
                if (index == index1) {
                    update1
                } else {
                    update2
                }
            } else {
                if (index == index1) {
                    expected1
                } else {
                    expected2
                }
            }
        }

        fun apply() {
            val installed = install()
            updateStatus(installed)
            updateCells()
        }

        private fun install(): Boolean {
            if (status.value != UNDECIDED) return false

            return installInto1() && installInto2()
        }

        private fun installInto1(): Boolean {
            while (true) {
                when (val value = array[index1].value) {
                    // expected value, let's try to install the descriptor
                    expected1 -> {
                        if (array[index1].compareAndSet(expected1, this)) {
                            // installed successfully
                            return true
                        }
                    }

                    // the descriptor is already installed
                    this -> return true

                    // another descriptor, let's help
                    is AtomicArrayWithCAS2<*>.CAS2Descriptor -> value.apply()
                    is AtomicArrayWithCAS2<*>.DCSSDescriptor -> value.help()

                    // unexpected value
                    else -> return false
                }
            }
        }

        private fun installInto2(): Boolean {
            dcssDesc.apply()
            return dcssDesc.status.value == SUCCESS
        }

        private fun updateStatus(installed: Boolean) {
            // if we managed to set the descriptor into both cells
            if (installed) {
                status.compareAndSet(UNDECIDED, SUCCESS)
            } else {
                status.compareAndSet(UNDECIDED, FAILED)
            }
        }

        private fun updateCells() {
            if (status.value == SUCCESS) {
                array[index1].compareAndSet(this, update1)
                array[index2].compareAndSet(this, update2)
            } else {
                array[index1].compareAndSet(this, expected1)
                array[index2].compareAndSet(this, expected2)
            }
        }
    }

    inner class DCSSDescriptor(
        private val index1: Int,
        private val expected1: E?,
        private val cas2Desc: CAS2Descriptor,
        private val expectedStatus: Status
    ) {
        val status = atomic(UNDECIDED)

        fun getValue(): E? {
            return if (status.value == SUCCESS) {
                cas2Desc.getValue(index1)
            } else {
                expected1
            }
        }

        fun apply() {
            val installed = install()
            updateStatus(installed)
            updateCell()
        }

        fun help() {
            updateStatus(true)
            updateCell()
        }

        private fun install(): Boolean {
            while (true) {
                if (status.value != UNDECIDED) return false

                val value1 = array[index1].value
                val cas2Status = cas2Desc.status.value
                when {
                    // the descriptor is already installed
                    value1 == this -> return true

                    // another descriptor, help and try again
                    value1 is AtomicArrayWithCAS2<*>.DCSSDescriptor -> value1.help()
                    value1 is AtomicArrayWithCAS2<*>.CAS2Descriptor -> value1.apply()

                    // expected values, try to install the descriptor
                    value1 == expected1 && cas2Status == expectedStatus -> {
                        if (array[index1].compareAndSet(expected1, this)) {
                            // installed successfully
                            return true
                        }
                    }

                    // some of the values are unexpected
                    else -> return false
                }
            }
        }

        private fun updateStatus(installed: Boolean) {
            if (installed) {
                status.compareAndSet(UNDECIDED, SUCCESS)
            } else {
                status.compareAndSet(UNDECIDED, FAILED)
            }
        }

        private fun updateCell() {
            if (status.value == SUCCESS) {
                array[index1].compareAndSet(this, cas2Desc)
            } else {
                array[index1].compareAndSet(this, expected1)
            }
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}