//package day3

import day3.AtomicArrayWithCAS2Simplified.Status.*
import kotlinx.atomicfu.*
import java.lang.IllegalStateException


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

        return if (cell is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
            when (cell.status.value) {
                UNDECIDED, FAILED -> when (index) {
                    cell.index1 -> cell.expected1
                    cell.index2 -> cell.expected2
                    else -> throw IllegalStateException()
                }

                SUCCESS -> when (index) {
                    cell.index1 -> cell.update1
                    cell.index2 -> cell.update2
                    else -> throw IllegalStateException()
                }
            } as E
        } else {
            cell as E
        }

    }

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
        // TODO: this implementation is not linearizable,
        // TODO: use CAS2Descriptor to fix it.
        // TODO: Note that only one thread can call CAS2!


        val descriptor = if (index1 < index2) {
            CAS2Descriptor(
                index1, expected1, update1,
                index2, expected2, update2,
            )
        } else {
            CAS2Descriptor(
                index2, expected2, update2,
                index1, expected1, update1,
            )
        }

        descriptor.apply()
        return descriptor.status.value === SUCCESS
    }

    inner class CAS2Descriptor(
        val index1: Int,
        val expected1: E,
        val update1: E,
        val index2: Int,
        val expected2: E,
        val update2: E
    ) {
        val status = atomic(UNDECIDED)

        fun apply() {
            // at this moment we have already applied to cell A
            val nextStatus = if (tryInstallDescriptor()) {
                SUCCESS
            } else {
                FAILED
            }

            status.compareAndSet(UNDECIDED, nextStatus)
            updateValues()
        }

        private fun tryInstallDescriptor(): Boolean {
            if (!this.tryInstallDescriptor(index1, expected1)) {
                return false
            }
            if (!this.tryInstallDescriptor(index2, expected2)) {
                return false
            }
            return true
        }

        private fun tryInstallDescriptor(index: Int, expected: E): Boolean {
            while (true) {
                when (val current = array[index].value) {
                    this -> {
                        return true
                    }
                    is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor -> {
                        current.apply()
                    }
                    expected -> {
                        if (array[index].compareAndSet(expected, this)) {
                            return true
                        } else {
                            continue
                        }
                    }
                    else -> {
                        return false
                    }
                }
            }
        }

        private fun updateValues() {
            if (status.value == SUCCESS) {
                array[index1].compareAndSet(this, update1)
                array[index2].compareAndSet(this, update2)
            } else {
                array[index1].compareAndSet(this, expected1)
                array[index2].compareAndSet(this, expected2)
            }
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}