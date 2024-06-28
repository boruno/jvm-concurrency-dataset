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
            var status = _status.get()
            status@ while (true) {
                when (status) {
                    UNDECIDED -> {
                        install1@ while (true) {
                            val witness1 = array.compareAndExchange(index1, expected1, this)

                            if (witness1 === expected1 || witness1 === this) {
                                // this thread published this descriptor to 1
                                status = _status.compareAndExchange(UNDECIDED, HALF)
                                when (status) {
                                    UNDECIDED -> {
                                        // this thread changed status
                                        status = HALF
                                    }

                                    HALF -> {

                                    }
                                    SUCCESS, FAILED -> {
                                        if (witness1 === expected1) {
                                            // 1 was published but another thread already completed this cas
                                            // => unpublish this cas
                                            array.compareAndExchange(index1, this, expected1)
                                            return
                                        }
                                    }
                                }
                                continue@status
                            } else if (witness1 is AtomicArrayWithCAS2<*>.CAS2Descriptor) {
                                witness1.apply()
                                continue@install1
                            } else {
                                status = _status.compareAndExchange(UNDECIDED, FAILED)
                                if (status == UNDECIDED) {
                                    // this thread changed status
                                    status = FAILED
                                }
                                continue@status
                            }
                        }
                    }

                    HALF -> {
                        install2@ while (true) {
                            val witness2 = array.compareAndExchange(index2, expected2, this)
                            if (witness2 === expected2 || witness2 === this) {
                                // this thread published this descriptor to 2
                                status = _status.compareAndExchange(HALF, SUCCESS)
                                when (status) {
                                    UNDECIDED -> error("")
                                    HALF -> {
                                        // this thread changed status
                                        status = SUCCESS
                                    }
                                    SUCCESS, FAILED -> {
                                        if (witness2 == expected2) {
                                            // 2 was published but another thread already completed this cas
                                            // => unpublish this cas
                                            array.compareAndExchange(index2, this, expected2)
                                            return
                                        }
                                    }
                                }
                                continue@status
                            } else if (witness2 is AtomicArrayWithCAS2<*>.CAS2Descriptor) {
                                witness2.apply()
                                continue@install2
                            } else {
                                status = _status.compareAndExchange(HALF, FAILED)
                                if (status == HALF) {
                                    // this thread changed status
                                    status = FAILED
                                }
                                continue@status
                            }
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

    private enum class Status {
        UNDECIDED,
        HALF,
        SUCCESS,
        FAILED
    }
}