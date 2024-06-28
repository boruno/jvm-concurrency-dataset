@file:Suppress("DuplicatedCode")

package day3

import day3.AtomicArrayWithCAS2.Status.*
import kotlinx.atomicfu.*

// This implementation never stores `null` values.
class AtomicArrayWithCAS2<E : Any>(size: Int, initialValue: E) {
    private val array = atomicArrayOfNulls<Any?>(size)

    init {
        // Fill array with the initial value.
        for (i in 0 until size) {
            array[i].value = initialValue
        }
    }

    fun get(index: Int): E {
        // TODO: the cell can store a descriptor
        val v = array[index].value
        return when (v) {
            is AtomicArrayWithCAS2<*>.CAS2Descriptor -> {
                v.getValue(index)
            }
            else -> v
        } as E
    }

    fun cas(index: Int, expected: E, update: E): Boolean {
        // TODO: the cell can store a descriptor
        while (true) {
            val cell = array[index].value
            when (cell) {
                is AtomicArrayWithCAS2<*>.CAS2Descriptor -> {
                    cell.apply()
                }
                expected -> {
                    if (array[index].compareAndSet(expected, update)) {
                        return true
                    }
                }
                else -> {
                    return false
                }
            }
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
            // TODO: Install the descriptor, update the status, and update the cells;
            // TODO: create functions for each of these three phases.
            install()
            applyLogical()
            applyPhysical()
        }

        private fun install() {
            if (status.value != UNDECIDED) {
                return
            }
            if (index1 < index2) {
                installCell(index1, expected1)
                installCell(index2, expected2)
            } else {
                installCell(index2, expected2)
                installCell(index1, expected1)
            }
        }

        private fun installCell(index: Int, expected: E) {
            while (true) {
                val cell = array[index].value
                when (cell) {
                    this -> {
                        break
                    }
                    is AtomicArrayWithCAS2<*>.CAS2Descriptor -> {
                        cell.apply()
                    }
                    expected -> {
                        if (array[index].compareAndSet(expected, this)) {
                            break
                        }
                    }
                    else -> {
                        status.compareAndSet(UNDECIDED, FAILED)
                        break
                    }
                }
            }
        }

        private fun applyLogical() {
            status.compareAndSet(UNDECIDED, SUCCESS)
        }

        private fun applyPhysical() {
            when (status.value) {
                SUCCESS -> {
                    array[index1].compareAndSet(this, update1)
                    array[index2].compareAndSet(this, update2)
                }
                FAILED -> {
                    array[index1].compareAndSet(this, expected1)
                    array[index2].compareAndSet(this, expected2)
                }
                UNDECIDED -> throw Error("applyPhysical UNDECIDED")
            }
        }

        fun getValue(index: Int): E {
            return when (status.value) {
                SUCCESS -> {
                    when (index) {
                        index1 -> update1
                        index2 -> update2
                        else -> throw Error("Wrong idx SUCCESS: $index")
                    }
                }
                FAILED, UNDECIDED -> {
                    when (index) {
                        index1 -> expected1
                        index2 -> expected2
                        else -> throw Error("Wrong idx FAILED: $index")
                    }
                }
            }
        }
    }


    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}