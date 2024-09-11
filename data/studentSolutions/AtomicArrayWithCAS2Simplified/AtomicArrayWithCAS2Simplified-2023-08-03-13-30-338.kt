package day3

import day3.AtomicArrayWithCAS2Simplified.Status.*
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls


// This implementation never stores `null` values.
@Suppress("DuplicatedCode")
class AtomicArrayWithCAS2Simplified<E : Any>(size: Int, initialValue: E) {
    private val array = atomicArrayOfNulls<Any?>(size)

    init {
        // Fill array with the initial value.
        for (i in 0 until size) {
            array[i].value = initialValue
        }
    }

    fun get(index: Int): E {
        val value = array[index].value
        return if (value is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
            value.getValue(index) as E
        } else {
            value as E
        }
    }

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
        if (index1 > index2) {
            return cas2(
                index2, expected2, update2,
                index1, expected1, update1
            )
        }
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

        init {
            require(index1 < index2)
        }

        fun getValue(index: Int): E {
            return when (index) {
                index1 -> if (status.value === SUCCESS) update1 else expected1
                index2 -> if (status.value === SUCCESS) update2 else expected2
                else -> error("opa")
            }
        }

        val status = atomic(UNDECIDED)

        fun apply() {
            while (true) {
                val valueBefore1 = getValueToOverride(index1, expected1)
                if (valueBefore1 == null) {
                    status.value = FAILED
                    return
                }
                if (!array[index1].compareAndSet(valueBefore1, this)) {
                    continue // try again
                }
                val valueBefore2 = getValueToOverride(index2, expected2)
                if (valueBefore2 == null) {
                    status.value = FAILED
                    return
                }
                if (!array[index2].compareAndSet(valueBefore2, this)) {
                    array[index1].compareAndSet(this, expected1) // reset 1 unless somebody already changed it
                    continue // try again
                }
                status.value = SUCCESS
                array[index1].compareAndSet(this, update1)
                array[index2].compareAndSet(this, update2)
            }
        }

        private fun getValueToOverride(index: Int, expected: E): Any? {
            val valueOrDescriptor = array[index].value
            val value = if (valueOrDescriptor is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
                if (valueOrDescriptor.status.value === UNDECIDED) {
                    return null
                }
                // descriptor is SUCCESS or FAILED state => treat as a value
                valueOrDescriptor.getValue(index)
            } else {
                valueOrDescriptor
            }
            if (value !== expected) {
                return null
            }
            return valueOrDescriptor
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}