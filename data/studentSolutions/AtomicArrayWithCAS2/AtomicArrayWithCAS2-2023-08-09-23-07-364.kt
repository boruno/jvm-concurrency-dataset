@file:Suppress("DuplicatedCode", "UNCHECKED_CAST")

//package day3

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

    fun get(index: Int): E? {
        while (true) {
            val element = array[index]
            if (element is AtomicArrayWithCAS2<*>.CAS2Descriptor) {
                if (element.status.get() == Status.SUCCESS) {
                    return element.getUpdate(index) as E
                }
                return element.getExpected(index) as E
            }
            if (element is AtomicArrayWithCAS2<*>.DCSSDescriptor) {
                element.complete()
                continue
            }
            return element as E
        }
    }

    fun cas2(
        index1: Int, expected1: E?, update1: E?,
        index2: Int, expected2: E?, update2: E?
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
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
        return descriptor.status.get() === Status.SUCCESS
    }

    private fun getAndCAS(index: Int, expected: Any?, newValue: Any?): Any? {
        while (true) {
            val value = array.get(index)
            if (value != expected) return value
            if (array.compareAndSet(index, expected, newValue)) return expected
        }
    }

    inner class DCSSDescriptor(
        val index: Int,
        val expected: Any?,
        val update: Any?,
        val status: AtomicReference<Status>
    ) {
        fun invoke(): Boolean {
            var r: Any?
            do {
                r = getAndCAS(index, expected, this)
                if (r is AtomicArrayWithCAS2<*>.DCSSDescriptor) r.complete()
            } while (r is AtomicArrayWithCAS2<*>.DCSSDescriptor)
            if (r == expected) return complete()
            return false
        }

        fun complete(): Boolean {
            return if (status.get() == Status.UNDECIDED) {
                array.compareAndSet(index, this, update)
            } else {
                array.compareAndSet(index, this, expected)
                false
            }
        }
    }

    inner class CAS2Descriptor(
        val index1: Int,
        val expected1: E,
        val update1: E,
        val index2: Int,
        val expected2: E,
        val update2: E
    ) {
        val status = AtomicReference(Status.UNDECIDED)

        private fun applyLogically(): Boolean {
            if (!install(index1)) return false
            return install(index2)
        }

        private fun install(index: Int): Boolean {
            while (true) {
                if (status.get() != Status.UNDECIDED) return false
                val state = array.get(index)
                if (state is AtomicArrayWithCAS2<*>.CAS2Descriptor) {
                    if (state == this) return true
                    state.apply()
                    continue
                }
                if (state == getExpected(index)) {
                    if (dcss(index, state, this, status)) return true else continue
                }
                return false
            }
        }

        fun getExpected(index: Int) = if (index == index1) expected1 else expected2

        fun getUpdate(index: Int) = if (index == index1) update1 else update2

        private fun applyPhysically() {
            if (status.get() == Status.SUCCESS) {
                array.compareAndSet(index1, this, update1)
                array.compareAndSet(index2, this, update2)
                return
            }
            if (status.get() == Status.FAILED) {
                array.compareAndSet(index1, this, expected1)
                array.compareAndSet(index2, this, expected2)
            }
        }

        private fun updateStatus(success: Boolean) {
            status.compareAndSet(Status.UNDECIDED, if (success) Status.SUCCESS else Status.FAILED)
        }

        fun apply() {
            val success = applyLogically()
            updateStatus(success)
            applyPhysically()
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }

    fun dcss(
        index: Int,
        expectedCellState: Any?,
        updateCellState: Any?,
        statusReference: AtomicReference<Status>,
    ): Boolean {
        return DCSSDescriptor(index, expectedCellState, updateCellState, statusReference).invoke()
    }
}

