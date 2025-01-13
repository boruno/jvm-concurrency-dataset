//package day3

import AtomicArrayWithCAS2Simplified.Status.*
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
        val value = array[index].value
        return when {
            value is AtomicArrayWithCAS2SingleWriter<*>.CAS2Descriptor -> when (value.status.value) {
                AtomicArrayWithCAS2SingleWriter.Status.SUCCESS -> (if (index == value.index1) value.update1 else value.update2) as E
                else -> (if (index == value.index1) value.expected1 else value.expected2) as E
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
            val (cix1, cix2) = if (index1 > index2) index1 to index2 else index2 to index1
            val (e1, e2) = if (cix1 == index1) expected1 to expected2 else expected2 to expected1
            val (u1, u2) = if (cix1 == index1) update1 to update2 else update2 to update1

            while (!array[cix1].compareAndSet(e1, this)) {
                val curState = array[cix1].value
                if (curState == this) {
                    break
                }
                if (curState is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
                    curState.apply()
                    continue
                } else {
                    if (curState != e1) {
                        status.compareAndSet(UNDECIDED, FAILED)
                        break
                    }
                }
            }
            while (!array[cix2].compareAndSet(e2, this)) {
                val curState = array[cix2].value
                if (curState == this) {
                    break
                }
                if (curState is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
                    curState.apply()
                    continue
                } else {
                    if (curState != e2) {
                        status.compareAndSet(UNDECIDED, FAILED)
                        break
                    }
                }
            }
            val status = status.value
            if (status == SUCCESS) {
                array[cix1].compareAndSet(this, u1)
                array[cix2].compareAndSet(this, u2)
            } else {
                array[cix1].compareAndSet(this, e1)
                array[cix2].compareAndSet(this, e2)
            }
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}