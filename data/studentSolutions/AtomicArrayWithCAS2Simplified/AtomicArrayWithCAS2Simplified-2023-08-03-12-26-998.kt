//package day3

import AtomicArrayWithCAS2Simplified.Status.*
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls


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
        return when (val value = array[index].value) {
            is AtomicArrayWithCAS2SingleWriter<*>.CAS2Descriptor -> {
                when (value.status.value) {
                    AtomicArrayWithCAS2SingleWriter.Status.UNDECIDED, AtomicArrayWithCAS2SingleWriter.Status.FAILED -> {
                        when {
                            value.index1 == index -> value.expected1
                            value.index2 == index -> value.expected2
                            else -> error("wrong indexes")
                        }
                    }

                    AtomicArrayWithCAS2SingleWriter.Status.SUCCESS -> {
                        when {
                            value.index1 == index -> value.update1
                            value.index2 == index -> value.update2
                            else -> error("wrong indexes")
                        }
                    }
                } as E
            }

            else -> value as E
        }
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
        private val index1: Int,
        private val expected1: E,
        private val update1: E,
        private val index2: Int,
        private val expected2: E,
        private val update2: E
    ) {
        val status = atomic(UNDECIDED)

        fun apply() {
            val maybeDescriptor1 = array[index1].value
            if (maybeDescriptor1 === this) return
            (maybeDescriptor1 as? AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor)?.apply()
            if (!array[index1].compareAndSet(expected1, this)) {
                status.value = FAILED
                return
            }

            val maybeDescriptor2 = array[index2].value
            if (maybeDescriptor2 === this) return
            (maybeDescriptor2 as? AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor)?.apply()
            if (!array[index2].compareAndSet(expected2, this)) {
                array[index1].compareAndSet(this, expected1)
                status.value = FAILED
                return
            }
            status.value = SUCCESS
            array[index1].compareAndSet(this, update1)
            array[index2].compareAndSet(this, update2)
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}