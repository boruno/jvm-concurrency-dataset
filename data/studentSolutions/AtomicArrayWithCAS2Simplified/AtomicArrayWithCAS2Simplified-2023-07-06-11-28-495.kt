//package day3

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
        val v = array[index].value
        return (if (v is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) v.get(index) else v) as E
    }

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
        val descriptor = CAS2Descriptor(index1, expected1, update1, index2, expected2, update2)
        return descriptor.apply()
    }

    inner class CAS2Descriptor(
        private val index1: Int,
        private val expected1: E,
        private val update1: E,
        private val index2: Int,
        private val expected2: E,
        private val update2: E
    ) {
        private val status = atomic(UNDECIDED)

        fun apply(): Boolean {
            if (!array[index1].compareAndSet(expected1, this)) {
                val v = array[index1].value
                if (v !== this || status.value == FAILED)
                    return false
            }
            if (!array[index2].compareAndSet(expected2, this)) {
                val v = array[index2].value
                if (v !== this || status.value == FAILED) {
                    if (!status.compareAndSet(
                        UNDECIDED,
                        FAILED
                    )) {
                        val actualStatus = status.value
                        require(actualStatus == FAILED) { "Must already be failed, but was $actualStatus" }
                    }
                    array[index1].compareAndSet(this, expected1) // If failed, someone else did a thing.
                    return false
                }
            }
            // All the descriptors are up. So, we're successful now.
            if (!status.compareAndSet(
                    UNDECIDED,
                    SUCCESS
                )) {
                val actualStatus = status.value
                require(actualStatus == SUCCESS) { "Must already be successful, but was $actualStatus" }
            }
            array[index1].compareAndSet(this, update1) // If failed, someone else did a thing.
            array[index2].compareAndSet(this, update2) // If failed, someone else did a thing.
            return true
        }

        fun get(index: Int): E {
            require(index == index1 || index == index2) { "get must be called on $index1 or $index2. Was for $index" }
            return when (status.value) {
                SUCCESS -> if (index1 == index) update1 else update2
                UNDECIDED, FAILED -> if (index1 == index) expected1 else expected2
            }
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}