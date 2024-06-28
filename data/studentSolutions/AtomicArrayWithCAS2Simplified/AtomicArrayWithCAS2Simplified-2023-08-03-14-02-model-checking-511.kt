package day3

import day3.AtomicArrayWithCAS2Simplified.Status.*
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
                            else -> continue
                        }

                        SUCCESS -> when (index) {
                            value.index1 -> value.update1
                            value.index2 -> value.update2
                            else -> continue
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

    private inner class CAS2Descriptor(
        val index1: Int,
        val expected1: E,
        val update1: E,
        val index2: Int,
        val expected2: E,
        val update2: E
    ) {
        val status = atomic(UNDECIDED)

        fun install(): Boolean {
            while (true) {
                when (status.value) {
                    SUCCESS -> return true
                    FAILED -> return false
                    else -> {}
                }
                var value = array[index1].value
                if (value != this && !array[index1].compareAndSet(expected1, this)) {
                    if (value is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
                        if (index1 != value.index2 && value.status.value == UNDECIDED) {
                            value.apply()
                        }
                    }
                    continue
                }
                value = array[index2].value
                if (value != this && !array[index2].compareAndSet(expected2, this)) {
                    if (value is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
                        if (index2 != value.index1 && value.status.value == UNDECIDED) {
                            value.apply()
                            continue
                        }
                    }
                    return false
                }
                return true
            }
        }

        fun uninstall() {
            status.compareAndSet(UNDECIDED, FAILED)
            array[index1].compareAndSet(this, expected1)
            array[index2].compareAndSet(this, expected2)
        }

        fun updateLogically() = status.value == SUCCESS || status.compareAndSet(UNDECIDED, SUCCESS)

        fun updatePhysically() = status.value == SUCCESS || status.value == UNDECIDED &&
                (array[index1].value == update1 || array[index1].compareAndSet(this, update1)) &&
                (array[index2].value == update2 || array[index2].compareAndSet(this, update2))

        fun rollback() {
            status.compareAndSet(UNDECIDED, FAILED)
            array[index1].compareAndSet(update1, expected1)
            array[index2].compareAndSet(update2, expected2)
        }

        fun apply() {
            when {
                !install() || !updateLogically() -> uninstall()
                !updatePhysically() -> rollback()
                else -> status.compareAndSet(UNDECIDED, SUCCESS)
            }
        }
    }

    private enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}