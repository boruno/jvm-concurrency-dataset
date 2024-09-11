@file:Suppress("DuplicatedCode")

package day3

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
        // TODO: the cell can store a descriptor
        return when (val value = array[index].value) {
            is AtomicArrayWithCAS2<*>.CAS2Descriptor -> valueFromCAS2Desc(value, index)

            is AtomicArrayWithCAS2<*>.DCSSDescriptor -> {
                if (value.status.value == Status.SUCCESS) {
                    valueFromCAS2Desc(value.update, index)
                } else {
                    value.expected as E?
                }
            }
            else -> value as E?
        }
    }

    private fun valueFromCAS2Desc(desc: AtomicArrayWithCAS2<*>.CAS2Descriptor, index: Int): E? {
        return if (desc.status.value == Status.SUCCESS) {
            if (index == desc.index1) desc.update1 as E? else desc.update2 as E?
        } else {
            if (index == desc.index1) desc.expected1 as E? else desc.expected2 as E?
        }
    }

    fun cas(index: Int, expected: E?, update: E?): Boolean {
        // TODO: the cell can store a descriptor
        while (true) {
            when (val value = array[index].value) {
                is AtomicArrayWithCAS2<*>.CAS2Descriptor -> when (value.status.value) {
                    Status.UNDECIDED -> value.apply()
                    Status.SUCCESS -> updateValues(value, true)
                    Status.FAILED -> updateValues(value, false)
                }
                is AtomicArrayWithCAS2<*>.DCSSDescriptor -> when (value.status.value) {
                    Status.UNDECIDED -> value.proceed(true)
                    Status.SUCCESS -> value.updateValue(true)
                    Status.FAILED -> value.updateValue(false)
                }
                expected -> if (array[index].compareAndSet(expected, update)) return true
                else -> return false
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
        require(index1 != index2) { "The indices should be different" }
        val descriptor = CAS2Descriptor(
            index1 = index1, expected1 = expected1, update1 = update1,
            index2 = index2, expected2 = expected2, update2 = update2
        )
        descriptor.apply()
        return descriptor.status.value === Status.SUCCESS
    }

    inner class CAS2Descriptor(
        val index1: Int,
        val expected1: E?,
        val update1: E?,
        val index2: Int,
        val expected2: E?,
        val update2: E?
    ) {
        val status = atomic(Status.UNDECIDED)

        val dcssDesc: AtomicRef<DCSSDescriptor?> = atomic(null)

        fun apply() {
            // TODO: Install the descriptor, update the status, and update the cells;
            // TODO: create functions for each of these three phases.

            if (install() && updateStatus(Status.SUCCESS)) {
                updateValues(this, true)
            } else if (updateStatus(Status.FAILED)) {
                updateValues(this, false)
            }
        }

        private fun install(): Boolean {
            return if (index1 > index2)
                installTo(index1, expected1) && installTo(index2, expected2)
            else
                installTo(index2, expected2) && installTo(index1, expected1)
        }

        private fun installTo(idx: Int, expected: E?): Boolean {
            while (true) {
                when (val value = array[idx].value) {
                    this -> return true

                    is AtomicArrayWithCAS2<*>.CAS2Descriptor -> when (value.status.value) {
                        Status.UNDECIDED -> value.apply()
                        Status.SUCCESS -> updateValues(value, true)
                        Status.FAILED -> updateValues(value, false)
                    }
                    is AtomicArrayWithCAS2<*>.DCSSDescriptor -> when (value.status.value) {
                        Status.UNDECIDED -> value.proceed(true)
                        Status.SUCCESS -> value.updateValue(true)
                        Status.FAILED -> value.updateValue(false)
                    }
                    expected -> {
                        // DCSS must be here (ABA problem):
                        if (dcssWithStatus(idx, expected, this)) return true
                    }
                    else -> return false
                }
            }
        }

        private fun updateStatus(value: Status): Boolean {
            return status.compareAndSet(Status.UNDECIDED, value)
        }
    }

    private fun updateValues(desc: AtomicArrayWithCAS2<*>.CAS2Descriptor, success: Boolean) {
        if (success) {
            array[desc.index1].compareAndSet(desc, desc.update1)
            array[desc.index2].compareAndSet(desc, desc.update2)
        } else {
            // DCSS can be here!
            array[desc.index1].compareAndSet(desc, desc.expected1)
            array[desc.index1].compareAndSet(desc.dcssDesc, desc.expected1)
            array[desc.index2].compareAndSet(desc, desc.expected2)
            array[desc.index1].compareAndSet(desc.dcssDesc, desc.expected1)
        }
    }

    fun dcssWithStatus(index: Int, expected: E?, update: CAS2Descriptor): Boolean {

        val descriptor = DCSSDescriptor(index = index, expected = expected, update = update)
        update.dcssDesc.value = descriptor
        descriptor.apply()
        return descriptor.status.value === Status.SUCCESS
    }

    inner class DCSSDescriptor(
        val index: Int,
        val expected: E?,
        val update: CAS2Descriptor,
    ) {
        val status = atomic(Status.UNDECIDED)

        fun apply() {
            proceed(install())
        }

        fun proceed(installed: Boolean) {
            if (installed && update.status.value == Status.UNDECIDED && updateStatus(Status.SUCCESS)) {
                updateValue(true)
            } else if (updateStatus(Status.FAILED)) {
                updateValue(false)
            }
        }

        private fun install(): Boolean {
            while (true) {
                when (val value = array[index].value) {
                    this -> return true
                    is AtomicArrayWithCAS2<*>.CAS2Descriptor -> when (value.status.value) {
                        Status.UNDECIDED -> value.apply()
                        Status.SUCCESS -> updateValues(value, true)
                        Status.FAILED -> updateValues(value, false)
                    }
                    is AtomicArrayWithCAS2<*>.DCSSDescriptor -> when (value.status.value) {
                        Status.UNDECIDED -> value.proceed(true)
                        Status.SUCCESS -> value.updateValue(true)
                        Status.FAILED -> value.updateValue(false)
                    }
                    expected -> {
                        if (array[index].compareAndSet(expected, this)) return true
                    }
                    else -> return false
                }
            }
        }

        private fun updateStatus(value: Status): Boolean {
            return status.compareAndSet(Status.UNDECIDED, value)
        }

        fun updateValue(success: Boolean) {
            if (success) {
                array[index].compareAndSet(this, update)
            } else {
                array[index].compareAndSet(this, expected)
            }
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}