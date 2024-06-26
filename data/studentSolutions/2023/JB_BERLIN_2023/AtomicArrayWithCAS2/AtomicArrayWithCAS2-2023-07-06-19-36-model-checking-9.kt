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

    fun get(index: Int): E? {
        while (true) {
            val v = array[index].value
            if (v is AtomicArrayWithCAS2<*>.DCSSDescriptor) {
                v.apply()
                continue
            }
            if (v is AtomicArrayWithCAS2<*>.CAS2Descriptor) {
                v.apply()
                continue
            }
            @Suppress("UNCHECKED_CAST")
            return v as E?
        }
    }

    fun cas(index: Int, expected: E?, update: E?): Boolean {
        while (true) {
            val v = array[index].value
            when {
                (v === expected) -> {
                    if (array[index].compareAndSet(expected, update))
                        return true
                    continue
                }
                v is AtomicArrayWithCAS2<*>.DCSSDescriptor -> {
                    v.apply()
                    continue
                }
                v is AtomicArrayWithCAS2<*>.CAS2Descriptor -> {
                    v.apply()
                    continue
                }
                else -> {
                    return false
                }
            }
        }
    }

    private fun dcss(
        index1: Int, expected1: E?, update1: CAS2Descriptor,
    ): Boolean {
        return DCSSDescriptor(index1, expected1, update1).installAndApply()
    }

    fun cas2(
        index1: Int, expected1: E?, update1: E?,
        index2: Int, expected2: E?, update2: E?
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
        val descriptor = if (index1 < index2) {
            CAS2Descriptor(index1, expected1, update1, index2, expected2, update2)
        } else {
            CAS2Descriptor(index2, expected2, update2, index1, expected1, update1)
        }
        return descriptor.apply()
    }

    inner class CAS2Descriptor(
        private val index1: Int,
        private val expected1: E?,
        private val update1: E?,
        private val index2: Int,
        private val expected2: E?,
        private val update2: E?
    ) {
        init {
            require(index1 < index2) {
                "index1=$index1 must be less than index2=$index2"
            }
        }

        private val status = atomic(UNDECIDED)

        val undecided
            get() = status.value == UNDECIDED

        fun apply(): Boolean {
            if (!installDescriptor(index1, expected1))
                return finalize(FAILED)
            if (!installDescriptor(index2, expected2))
                return finalize(FAILED)
            return finalize(SUCCESS)
        }

        private fun installDescriptor(index: Int, expected: E?): Boolean {
            while (true) {
                val v = array[index].value
                when {
                    v === this -> return true
                    v === expected -> {
                        if (dcss(index, expected, this))
                            return true
                    }
                    v is AtomicArrayWithCAS2<*>.DCSSDescriptor -> {
                        v.apply()
                        continue
                    }
                    v is AtomicArrayWithCAS2<*>.CAS2Descriptor -> {
                        v.apply()
                        continue
                    }
                    else -> return false
                }
            }
        }

        private fun finalize(s: Status): Boolean {
            status.compareAndSet(UNDECIDED, s)
            return when (status.value) {
                SUCCESS -> {
                    array[index2].compareAndSet(this, update2) // If failed, someone else did a thing.
                    array[index1].compareAndSet(this, update1) // If failed, someone else did a thing.
                    true
                }

                FAILED -> {
                    array[index2].compareAndSet(this, expected2) // If failed, someone else did a thing.
                    array[index1].compareAndSet(this, expected1) // If failed, someone else did a thing.
                    false
                }

                UNDECIDED -> {
                    error("status cannot be UNDECIDED")
                }
            }
        }
    }

    inner class DCSSDescriptor(
        private val index: Int,
        private val expected: E?,
        private val update: CAS2Descriptor,
    ) {
        private val state = atomic(DCSSState.UNDECIDED)

        fun installAndApply(): Boolean {
            if (!install())
                return finalize(DCSSState.FAILURE)
            return apply()
        }

        fun apply(): Boolean {
            if (!update.undecided)
                return finalize(DCSSState.FAILURE)
            return finalize(DCSSState.SUCCESS)
        }

        private fun install(): Boolean {
            while (true) {
                val v = array[index].value
                when {
                    (v === expected) -> {
                        if (array[index].compareAndSet(expected, this))
                            return true
                        continue
                    }
                    (v === this) -> {
                        return true
                    }
                    v is AtomicArrayWithCAS2<*>.DCSSDescriptor -> {
                        v.apply()
                        continue
                    }
                    v is AtomicArrayWithCAS2<*>.CAS2Descriptor -> {
                        v.apply()
                        continue
                    }
                    else -> {
                        return false
                    }
                }
            }
        }

        private fun finalize(s: DCSSState): Boolean {
            state.compareAndSet(DCSSState.UNDECIDED, s)
            return when (state.value) {
                DCSSState.UNDECIDED -> error("Can't be here")
                DCSSState.SUCCESS -> {
                    array[index].compareAndSet(this, update)
                    true
                }
                DCSSState.FAILURE -> {
                    array[index].compareAndSet(this, expected)
                    false
                }
            }
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }

    private enum class DCSSState {
        UNDECIDED,
        SUCCESS,
        FAILURE,
    }
}