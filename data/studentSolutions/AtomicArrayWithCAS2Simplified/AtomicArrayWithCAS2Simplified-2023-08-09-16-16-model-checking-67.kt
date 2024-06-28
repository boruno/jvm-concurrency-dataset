@file:Suppress("DuplicatedCode", "UNCHECKED_CAST")

package day3

import day3.AtomicArrayWithCAS2Simplified.Status.*
import java.util.concurrent.atomic.*


// This implementation never stores `null` values.
class AtomicArrayWithCAS2Simplified<E : Any>(size: Int, initialValue: E) {
    private val array = AtomicReferenceArray<Any?>(size)

    init {
        // Fill array with the initial value.
        for (i in 0 until size) {
            array[i] = initialValue
        }
    }

    fun get(index: Int): E {
        val v = array[index]
        when (v) {
            is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor -> {
                if (v.status.get() == SUCCESS) {
                    val result = if (v.index1 == index) {
                        v.update1 as E
                    } else {
                        v.update2 as E
                    }
                    // help?
                    return result
                } else {
                    return if (v.index1 == index) {
                        v.expected1 as E
                    } else {
                        v.expected2 as E
                    }
                }
            }

            else -> return v as E
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
        return descriptor.status.get() === SUCCESS
    }

    inner class CAS2Descriptor(
        val index1: Int,
        val expected1: E,
        val update1: E,
        val index2: Int,
        val expected2: E,
        val update2: E
    ) {
        val status = AtomicReference(UNDECIDED)

        fun apply() {
            // TODO: Install the descriptor, update the status, and update the cells;
            // TODO: create functions for each of these three phases.

            val idx = if (index1 < index2) listOf(index1, index2) else listOf(index2, index1)
            val exp = if (index1 < index2) listOf(expected1, expected2) else listOf(expected2, expected1)
            if (status.get() == UNDECIDED) {
                // ^ need the check because apply can be called by helpers,
                // and we don't want to reinstall a finished descriptor
                val installed = installTo(idx[0], exp[0]) && installTo(idx[1], exp[1])
                finishLogically(installed)
            }

            finishPhysically()
        }

        private fun installTo(index: Int, expected: Any): Boolean {
            while (true) {
//                val v = array.compareAndExchange(index, expected, this)
                val v = array[index]
                if (v == this) {
                    // someone helped us
                    return true
                }
                if (v !is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
                    // unexpected value, fail and exit
                    return false
                }
                if (v == expected) {
                    return array.compareAndSet(index, expected, this)
                    //return true
                }
                // foreign descriptor, help it, and repeat
                v.apply()
            }
        }

        fun finishLogically(success: Boolean) {
            status.compareAndSet(UNDECIDED, if (success) SUCCESS else FAILED)
        }

        private fun finishPhysically() {
            if (status.get() == SUCCESS) {
                updatePhysically()
            } else {
                rollbackPhysically()
            }
        }

        private fun updatePhysically() {
            array.compareAndSet(index1, this, update1)
            array.compareAndSet(index2, this, update2)
        }

        fun rollbackPhysically() {
            array.compareAndSet(index1, this, expected1)
            array.compareAndSet(index2, this, expected2)
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}