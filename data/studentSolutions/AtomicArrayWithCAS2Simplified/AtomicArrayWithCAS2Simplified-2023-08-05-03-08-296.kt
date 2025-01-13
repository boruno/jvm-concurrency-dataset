//package day3

import AtomicArrayWithCAS2Simplified.Status.*
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls

class AtomicArrayWithCAS2Simplified<E : Any>(size: Int, initialValue: E) {
    private val array = atomicArrayOfNulls<Any?>(size)

    init {
        for (i in 0 until size) {
            array[i].value = initialValue
        }
    }

    fun get(index: Int): E {
        while (true) {
            @Suppress("UNCHECKED_CAST")
            return when (val value = array[index].value) {
                is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor -> {
                    when (value.status.value) {
                        UNDECIDED, FAILED -> when (index) {
                            value.index1 -> value.expected1
                            value.index2 -> value.expected2
                            else -> error("Unexpected descriptor in index #$index")
                        }

                        SUCCESS -> when (index) {
                            value.index1 -> value.update1
                            value.index2 -> value.update2
                            else -> error("Unexpected descriptor in index #$index")
                        }
                    }
                }

                else -> value
            } as E
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
        return descriptor.status.value == SUCCESS
    }

    private val cas2Id = atomic(0)

    private inner class CAS2Descriptor(
        val index1: Int, val expected1: E, val update1: E,
        val index2: Int, val expected2: E, val update2: E
    ) {
        val id = cas2Id.getAndIncrement()
        val status = atomic(UNDECIDED)

        fun apply() {
            if (install()) {
                updateLogically()
                updatePhysically()
            } else {
                uninstall()
            }
        }

        fun install(): Boolean {
            while (true) {
                val status = status.value
                val value1 = array[index1].value
                val value2 = array[index2].value
                return when {
                    // already installed
                    value1 == this && value2 == this -> true
                    // already updated logically
                    status == SUCCESS -> true
                    // already updated physically
                    value1 == update1 && value2 == update2 -> true
                    // failed to install
                    status == FAILED -> false
                    // help to complete
                    value1 != this && value1 is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor -> {
                        if (id < value1.id) {
                            // the other one has priority
                            value1.apply()
                            continue
                        } else {
                            false
                        }
                    }

                    value2 != this && value2 is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor -> {
                        if (id < value2.id) {
                            // the other one has priority
                            value2.apply()
                            continue
                        } else {
                            false
                        }
                    }
                    // unexpected value
                    value1 != this && value1 != expected1 -> false
                    value2 != this && value2 != expected2 -> false
                    // installing
                    value1 == expected1 && !array[index1].compareAndSet(value1, this) -> false
                    value2 == expected2 && !array[index2].compareAndSet(value2, this) -> false
                    else -> continue
                }
            }
        }

        private fun installThisOrApply(other: AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
            if (id < other.id) {
                // this one has priority
                array[index1].compareAndSet(other, this)
            } else {
                // the other one has priority
                other.apply()
            }
        }

        fun updateLogically() {
            check(status.value == SUCCESS || status.compareAndSet(UNDECIDED, SUCCESS))
        }

        fun updatePhysically() {
            check(status.value == SUCCESS)
            check(array[index1].value == update1 || array[index1].compareAndSet(this, update1))
            check(array[index2].value == update2 || array[index2].compareAndSet(this, update2))
        }

        fun uninstall() {
            check(status.value == FAILED || status.compareAndSet(UNDECIDED, FAILED))
            check(array[index1].value == expected1 || array[index1].compareAndSet(this, expected1))
            check(array[index2].value == expected2 || array[index2].compareAndSet(this, expected2))
        }
    }

    private enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}