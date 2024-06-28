package day3

import day3.AtomicArrayWithCAS2Simplified.Status.*
import kotlinx.atomicfu.*


// This implementation never stores `null` values.
class AtomicArrayWithCAS2Simplified<E : Any>(size: Int, initialValue: E) {
    private val array = atomicArrayOfNulls<Any?>(size)

    init {
        // Fill array with the initial value.
        for (i in 0 until size) {
            array[i].value = initialValue
        }
    }

    fun get(index: Int): E {
        // TODO: the cell can store CAS2Descriptor
        val cellState = array[index].value
        if (cellState is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
            if (cellState.status.value == SUCCESS) {
                if (cellState.index1 == index) return cellState.update1 as E
                if (cellState.index2 == index) return cellState.update2 as E
            }
            else
            {
                if (cellState.index1 == index) return cellState.expected1 as E
                if (cellState.index2 == index) return cellState.expected2 as E
            }
        }
        return cellState as E
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
        return descriptor.status.value === SUCCESS
    }

    inner class CAS2Descriptor(
        public val index1: Int,
        public val expected1: E,
        public val update1: E,
        public val index2: Int,
        public val expected2: E,
        public val update2: E
    ) {
        val status = atomic(UNDECIDED)

        fun apply() {
            // TODO: Install the descriptor, update the status, and update the cells;
            // TODO: create functions for each of these three phases.
            while (true)
            {
                val elem1 = array[index1].value
                val elem2 = array[index2].value

                if (elem1 is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
                    if (elem1.status.value != UNDECIDED) {
                        elem1.applyStatus()
                        continue
                    }
                } else {
                    //if (array[index1].value == expected1) array[index1].value = this
                    if (!array[index1].compareAndSet(expected1, this)) {
                        status.compareAndSet(UNDECIDED, FAILED)
                        break
                    }
                }

                if (elem2 is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
                    if (elem2.status.value != UNDECIDED) {
                        elem2.applyStatus()
                        continue
                    }
                } else {
                    //if (array[index2].value == expected2) array[index2].value = this
                    if (!array[index2].compareAndSet(expected2, this)) {
                        status.compareAndSet(UNDECIDED, FAILED)
                        break
                    }
                }
            }
            status.compareAndSet(UNDECIDED, SUCCESS)
            applyStatus()
        }

        fun applyStatus()
        {
            if (status.value == SUCCESS) applySuccess()
            if (status.value == FAILED) applyFailed()
        }

        fun applySuccess() {
            val elem1 = array[index1].value
            if (elem1 is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor)
                array[index1].compareAndSet(elem1, update1)

            val elem2 = array[index2].value
            if (elem2 is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor)
                array[index2].compareAndSet(elem2, update2)
        }

        fun applyFailed()
        {
            val elem1 = array[index1].value
            if (elem1 is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor)
                array[index1].compareAndSet(elem1, expected1)

            val elem2 = array[index2].value
            if (elem2 is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor)
                array[index2].compareAndSet(elem2, expected2)
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}