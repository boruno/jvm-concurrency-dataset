@file:Suppress("DuplicatedCode")

package day3

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
        return descriptor._status.get() === SUCCESS
    }

    private inner class CAS2Descriptor(
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
                index1 -> if (_status.get() === SUCCESS) update1 else expected1
                index2 -> if (_status.get() === SUCCESS) update2 else expected2
                else -> error("opa")
            }
        }

        val _status: AtomicReference<Status> = AtomicReference(UNDECIDED)

        fun apply() {
            status@ while (true) {
                when (_status.get()) {
                    UNDECIDED -> {
                        if (firstCell()) {
                            return
                        }
                    }

                    HALF -> {
                        if (secondCell()) {
                            return
                        }
                    }

                    SUCCESS -> {
                        commit()
                        return
                    }

                    FAILED -> {
                        rollback()
                        return
                    }
                }
            }
        }

        private fun firstCell(): Boolean {
            val witness1 = array.compareAndExchange(index1, expected1, this)
            if (witness1 === expected1 || witness1 === this) {
                if (_status.compareAndSet(UNDECIDED, HALF)) {
                    if (secondCell()) {
                        return true
                    }
                }
            } else if (witness1 is AtomicArrayWithCAS2<*>.CAS2Descriptor) {
                witness1.apply()
            } else {
                if (_status.compareAndSet(UNDECIDED, FAILED)) {
                    rollback()
                    return true
                }
            }
            return false
        }

        private fun secondCell(): Boolean {
            val witness2 = array.compareAndExchange(index2, expected2, this)
            if (witness2 === expected2 || witness2 === this) {
                if (_status.compareAndSet(HALF, SUCCESS)) {
                    commit()
                    return true
                }
            } else if (witness2 is AtomicArrayWithCAS2<*>.CAS2Descriptor) {
                witness2.apply()
            } else {
                if (_status.compareAndSet(HALF, FAILED)) {
                    rollback()
                    return true
                }
            }
            return false
        }

        private fun commit() {
            array.compareAndSet(index1, this, update1)
            array.compareAndSet(index2, this, update2)
        }

        private fun rollback() {
            array.compareAndSet(index1, this, expected1)
            array.compareAndSet(index2, this, expected2)
        }
    }

    private enum class Status {
        UNDECIDED,
        HALF,
        SUCCESS,
        FAILED
    }
}