@file:Suppress("DuplicatedCode")

//package day3

import day3.AtomicArrayWithCAS2.Status.*
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicReferenceArray

// This implementation never stores `null` values.
@Suppress("WHEN_ENUM_CAN_BE_NULL_IN_JAVA")
class AtomicArrayWithCAS2<E : Any>(size: Int, initialValue: E) {
    private val array = AtomicReferenceArray<Any?>(size)

    init {
        // Fill array with the initial value.
        for (i in 0 until size) {
            array.set(i, initialValue)
        }
    }

    fun get(index: Int): E? {
        val value = array.get(index)
        return if (value is AtomicArrayWithCAS2<*>.CAS2Descriptor) {
            value.getValue(index) as E
        } else {
            value as E
        }
    }

    fun cas(index: Int, expected: E?, update: E?): Boolean {
        while (true) {
            val witness = array.compareAndExchange(index, expected, update)
            if (witness === expected) {
                return true
            } else if (witness is AtomicArrayWithCAS2<*>.CAS2Descriptor) {
                witness.apply()
            } else {
                return false
            }
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
        return descriptor.status.get() === SUCCESS
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
                index1 -> if (status.get() === SUCCESS) update1 else expected1
                index2 -> if (status.get() === SUCCESS) update2 else expected2
                else -> error("opa")
            }
        }

        val status: AtomicReference<Status> = AtomicReference(UNDECIDED)

        private fun install1(): Boolean {
            while (true) {
                val witness1 = array.compareAndExchange(index1, expected1, this)
                return if (witness1 === expected1 || witness1 === this) {
                    true
                } else if (witness1 is AtomicArrayWithCAS2<*>.CAS2Descriptor) {
                    witness1.apply()
                    continue // try again
                } else {
                    false
                }
            }
        }

        private fun install2(): Boolean {
            while (true) {
                val witness2 = array.compareAndExchange(index2, expected2, this)
                return if (witness2 === expected2 || witness2 === this) {
                    true
                } else if (witness2 is AtomicArrayWithCAS2<*>.CAS2Descriptor) {
                    witness2.apply()
                    continue
                } else {
                    false
                }
            }
        }

        fun apply() {
            while (true) {
                when (status.get()) {
                    UNDECIDED -> {
                        if (install1()) {
                            status.compareAndSet(UNDECIDED, HALF)
                        } else {
                            status.compareAndSet(UNDECIDED, FAILED)
                        }
                    }

                    HALF -> {
                        if (install2()) {
                            status.compareAndSet(HALF, SUCCESS)
                        } else {
                            status.compareAndSet(HALF, FAILED)
                        }
                    }

                    SUCCESS -> {
                        array.compareAndSet(index1, this, update1)
                        array.compareAndSet(index2, this, update2)
                        return
                    }

                    FAILED -> {
                        array.compareAndSet(index1, this, expected1)
                        array.compareAndSet(index2, this, expected2)
                        return
                    }
                }
            }
        }
    }

    enum class Status {
        UNDECIDED,
        HALF,
        SUCCESS,
        FAILED
    }
}