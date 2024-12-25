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
        @Suppress("UNCHECKED_CAST")
        return (if (v is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) v.get(index) else v) as E
    }

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
        val descriptor = if (index1 < index2) {
            CAS2Descriptor(index1, expected1, update1, index2, expected2, update2)
        } else {
            CAS2Descriptor(index2, expected2, update2, index1, expected1, update1)
        }
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
        init {
            require(index1 < index2) {
                "index1=$index1 must be less than index2=$index2"
            }
        }

        private val status = atomic(UNDECIDED)

        fun apply(): Boolean {
            while (true) {
                // ABA:
                // [0, 0] : cas2(0, 0, 1, 1, 0, 2) -> d1
                // [d1, 0]: sleep
                // other thread cas2(0, 1, 3, 1, 2, 0) -> d2
                // finish applying d1: [1, 2]
                // now apply d2: [3, 0]
                // thread 1 wakes and puts d1 into index 1:
                // [3, d1]: and thinks we're successful.
                // [3, 2]: HA!
                when (installDescriptor(index1, expected1)) {
                    InstallationResult.SUCCESS -> {}
                    InstallationResult.FAILURE -> return finalize(FAILED)
                    InstallationResult.RETRY -> continue
                }
                when (installDescriptor(index2, expected2)) {
                    InstallationResult.SUCCESS -> {}
                    InstallationResult.FAILURE -> return finalize(FAILED)
                    InstallationResult.RETRY -> continue
                }
                return finalize(SUCCESS)
            }
        }

        private fun installDescriptor(index: Int, expected: E): InstallationResult {
            if (status.value != SUCCESS)
                return InstallationResult.SUCCESS
            if (array[index].compareAndSet(expected, this))
                return InstallationResult.SUCCESS
            val v = array[index].value
            if (v === this)
                // We're already there. Let's go.
                return InstallationResult.SUCCESS
            if (v === expected)
                // Wait a minute. CAS from above might have succeeded. AGAIN!
                return InstallationResult.RETRY
            if (v !is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor)
                // There's something that's not a descriptor, and it's not expected. Failure.
                return InstallationResult.FAILURE
            // Okay, let's apply that descriptor and retry
            v.apply()
            return InstallationResult.RETRY
        }

        private fun finalize(s: Status): Boolean {
            status.compareAndSet(UNDECIDED, s)
            return when (status.value) {
                SUCCESS -> {
                    array[index2].compareAndSet(this, update2) // If failed, someone else did a thing.
                    array[index1].compareAndSet(this, update1) // If failed, someone else did a thing.
                    true
                }

                FAILED -> {
                    array[index2].compareAndSet(this, expected2) // If failed, someone else did a thing.
                    array[index1].compareAndSet(this, expected1) // If failed, someone else did a thing.
                    false
                }

                UNDECIDED -> {
                    error("status cannot be UNDECIDED")
                }
            }
        }

        fun get(index: Int): E {
            require(index == index1 || index == index2) { "get must be called on $index1 or $index2. Was for $index" }
            return when (status.value) {
                SUCCESS -> if (index1 == index) update1 else update2
                UNDECIDED, FAILED -> if (index1 == index) expected1 else expected2
            }
        }
    }

    enum class InstallationResult {
        FAILURE, SUCCESS, RETRY
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}