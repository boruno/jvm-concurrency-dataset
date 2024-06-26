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
        val cell = array[index].value
        return ((cell as? AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor)?.read(index) ?: cell) as E
    }

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
        val descriptor = if (index1 < index2) {
            CAS2Descriptor(
                index1 = index1, expected1 = expected1, update1 = update1,
                index2 = index2, expected2 = expected2, update2 = update2
            )
        } else {
            CAS2Descriptor(
                index1 = index2, expected1 = expected2, update1 = update2,
                index2 = index1, expected2 = expected1, update2 = update1
            )
        }
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
            completeRunningOperation()
            val (idx1, idx2) = install()
            setStatus(idx1, idx2)
            cleanup()
        }

        fun cleanup() {
            if (status.value === SUCCESS) {
                array[index1].compareAndSet(this, update1)
                array[index2].compareAndSet(this, update2)
            } else if (status.value === FAILED) {
                array[index1].compareAndSet(this, expected1)
                array[index2].compareAndSet(this, expected2)
            }
        }

        fun setStatus(idx1: Boolean, idx2: Boolean) {
            if (idx1 && idx2) {
                status.compareAndSet(UNDECIDED, SUCCESS)
            } else {
                status.compareAndSet(UNDECIDED, FAILED)
            }
        }

        fun install(): Pair<Boolean, Boolean> {
            val idx1 = array[index1].compareAndSet(expected1, this)
            val idx2 = array[index2].compareAndSet(expected2, this)
            return Pair(idx1, idx2)
        }

        fun read(index: Int): E? {
            return when (index) {
                index1 -> if (status.value === SUCCESS) update1 else expected1
                index2 -> if (status.value === SUCCESS) update2 else expected2
                else -> null
            }
        }

        fun completeRunningOperation() {
            val caS2Descriptor1 = array[index1].value as? AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor
            val caS2Descriptor2 = array[index2].value as? AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor

            if (caS2Descriptor1 != null && caS2Descriptor2 != null) {
                if (caS2Descriptor1 == caS2Descriptor2) {
                    caS2Descriptor1.setStatus(true, true)
                }
                else {
                    val otherForOne = array[caS2Descriptor1.index2].value as? AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor
                    if (otherForOne != null) {
                        otherForOne.setStatus(true, true)
                    }
                }
                caS2Descriptor1.cleanup()
                caS2Descriptor2.cleanup()
            }
            else if (caS2Descriptor1 == null && caS2Descriptor2 != null) {
                caS2Descriptor2.cleanup()
            }
            else if (caS2Descriptor1 != null) {
                if (array[index2].compareAndSet(expected2, this)) {
                    caS2Descriptor1.setStatus(true, true)
                }
                caS2Descriptor1.cleanup()
                caS2Descriptor1.cleanup()
            }
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}