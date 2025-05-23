//package day3

import AtomicArrayWithCAS2Simplified.Status.*
import kotlinx.atomicfu.atomic
import java.util.concurrent.atomic.AtomicReferenceArray


// This implementation never stores `null` values.
@Suppress("DuplicatedCode")
class AtomicArrayWithCAS2Simplified<E : Any>(size: Int, initialValue: E) {
    private val array = AtomicReferenceArray<Any?>(size)

    init {
        // Fill array with the initial value.
        for (i in 0 until size) {
            array.set(i, initialValue)
        }
    }

    fun get(index: Int): E {
        val value = array.get(index)
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
                when (status.value) {
                    UNDECIDED -> {
                        val witness = array.compareAndExchange(index1, expected1, this)
                        if (witness === expected1 || witness === this) {
                            // published OK
                            status.compareAndSet(UNDECIDED, PUBLISHED_1)
                            continue // on to the next state
                        } else if (witness is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
                            witness.apply()
                            continue
                        } else {
                            status.compareAndSet(UNDECIDED, FAILED)
                            return
                        }
                    }

                    PUBLISHED_1 -> {
                        val witness = array.compareAndExchange(index2, expected2, this)
                        if (witness === expected2 || witness === this) {
                            // published OK or another thread is helping me
                            status.compareAndSet(PUBLISHED_1, SUCCESS)
                            // finish
                            array.compareAndSet(index1, this, update1)
                            array.compareAndSet(index2, this, update2)
                            return
                        } else if (witness is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
                            witness.apply()
                            continue
                        } else {
                            status.compareAndSet(PUBLISHED_1, FAILED)
                            array.compareAndSet(index1, this, expected1) // revert 1
                            return
                        }
                    }

                    SUCCESS -> {
                        array.compareAndSet(index1, this, update1)
                        array.compareAndSet(index2, this, update2)
                        return // terminal state
                    }

                    FAILED -> {
                        array.compareAndSet(index1, this, expected1) // revert 1
                        return // terminal state
                    }
                }
            }
        }
    }

    enum class Status {
        UNDECIDED,
        PUBLISHED_1,
        SUCCESS, // == PUBLISHED_2
        FAILED
    }
}
