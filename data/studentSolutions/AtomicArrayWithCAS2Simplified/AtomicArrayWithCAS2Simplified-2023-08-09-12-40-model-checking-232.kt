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
        val state = array[index]
        return if (state is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
            if (state.status.get() == SUCCESS) {
                when (index) {
                    state.index1 -> state.update1
                    state.index2 -> state.update2
                    else -> error("Unknown index $index")
                }
            } else {
                when (index) {
                    state.index1 -> state.expected1
                    state.index2 -> state.expected2
                    else -> error("Unknown index $index")
                }
            } as E
        } else {
            state as E
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
            install()
            logically()
            physically()
        }

        private fun physically() {
            if (status.get() == SUCCESS) {
                array.compareAndSet(index1, this, update1)
                array.compareAndSet(index2, this, update2)
            } else {
                array.compareAndSet(index1, this, expected1)
                array.compareAndSet(index2, this, expected2)
            }
        }

        private fun logically() {
            val v1 = array.get(index1)
            if (v1 != this) {
                status.compareAndSet(UNDECIDED, FAILED)
            }
            val v2 = array.get(index2)
            if (v2 != this) {
                status.compareAndSet(UNDECIDED, FAILED)
            }
            status.compareAndSet(UNDECIDED, SUCCESS)
        }

        private fun install() {
            if (status.get() != UNDECIDED) {
                return
            }
            if (index1 < index2) {
                install(index1, expected1, this)
                install(index2, expected2, this)
            } else {
                install(index2, expected2, this)
                install(index1, expected1, this)
            }
        }

        private fun install(index: Int, expected: E, newValue: Any): Boolean {
            while (true) {
                if (!array.compareAndSet(index, expected, newValue)) {
                    val state = array.get(index)
                    if (state == this) {
                        break
                    } else if (state is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
                        state.apply()
                    } else {
                        if (state != expected) {
                            return false
                        }
                    }
                } else {
                    break
                }
            }
            return true
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}