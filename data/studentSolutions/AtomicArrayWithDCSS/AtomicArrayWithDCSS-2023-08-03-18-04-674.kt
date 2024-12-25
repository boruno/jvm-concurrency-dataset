//package day3

import day3.AtomicArrayWithDCSS.Status.*
import kotlinx.atomicfu.*

// This implementation never stores `null` values.
class AtomicArrayWithDCSS<E : Any>(size: Int, initialValue: E) {
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
            is AtomicArrayWithDCSS<*>.DCSSDescriptor -> {
                v.getValue(index)
            }
            else -> v
        } as E
    }

    fun cas(index: Int, expected: E, update: E): Boolean {
        // TODO: the cell can store a descriptor
        // todo help with descriptor
        while (true) {
            val cell = array[index].value
            when (cell) {
                is AtomicArrayWithDCSS<*>.DCSSDescriptor -> {
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

    fun dcss(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
        val descriptor = DCSSDescriptor(index1, expected1, update1, index2, expected2)
        descriptor.apply()
        return descriptor.status.value == SUCCESS
    }

    inner class DCSSDescriptor(
        private val index1: Int,
        private val expected1: E,
        private val update1: E,
        private val index2: Int,
        private val expected2: E
    ) {
        val status = atomic(UNDECIDED)

        fun apply() {
            install()
            applyLogical()
            applyPhysical()
        }

        private fun install() {
            // todo set FAILED when other expected value doesn't matches
            if (status.value != UNDECIDED) return
            while (true) {
                val cell = array[index1].value
                when (cell) {
                    this -> {
                        break
                    }
                    is AtomicArrayWithDCSS<*>.DCSSDescriptor -> {
                        cell.apply()
                    }
                    expected1 -> {
                        if (status.value != UNDECIDED) break
                        if (array[index1].compareAndSet(expected1, this)) {
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
            if (array[index2].value == expected2) {
                status.compareAndSet(UNDECIDED, SUCCESS)
            } else {
                status.compareAndSet(UNDECIDED, FAILED)
            }
        }

        private fun applyPhysical() {
            when (status.value) {
                SUCCESS -> {
                    array[index1].compareAndSet(this, update1)
                }
                FAILED -> {
                    array[index1].compareAndSet(this, expected1)
                }
                UNDECIDED -> throw Error("applyPhysical UNDECIDED")
            }
        }

        fun getValue(index: Int): E {
            return when (status.value) {
                SUCCESS -> {
                    when (index) {
                        index1 -> update1
                        else -> throw Error("Wrong idx SUCCESS: $index")
                    }
                }
                FAILED, UNDECIDED -> {
                    when (index) {
                        index1 -> expected1
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