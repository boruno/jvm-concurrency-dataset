package day3

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
        val item = array[index].value
        if (item is AtomicArrayWithCAS2SingleWriter<*>.CAS2Descriptor) {
            if (item.index1 == index) {
                return if (item.status.value != AtomicArrayWithCAS2SingleWriter.Status.SUCCESS) item.expected1 as E else item.update1 as E
            } else if (item.index2 == index) {
                return if (item.status.value != AtomicArrayWithCAS2SingleWriter.Status.SUCCESS) item.expected2 as E else item.update2 as E
            } else {
                throw IllegalStateException("Incorrect descriptor $item for index $index")
            }
        } else {
            return item as E
        }
    }

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
        // TODO: this implementation is not linearizable,
        // TODO: use CAS2Descriptor to fix it.

        val descriptor = if (index1 <= index2) {
            CAS2Descriptor(index1, expected1, update1, index2, expected2, update2)
        } else {
            CAS2Descriptor(index2, expected2, update2, index1, expected1, update1)
        }
        descriptor.apply()
        return descriptor.status.value == SUCCESS
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
            if (status.value === UNDECIDED) {
                val success = tryInstallDescriptors()
                if (success) {
                    status.compareAndSet(UNDECIDED, SUCCESS)
                }
                else {
                    status.compareAndSet(UNDECIDED, FAILED)
                }
            }
            updateValues()
        }

        private fun tryInstallDescriptors(): Boolean {
            if (!tryInstallDescriptor(index1, expected1)) return false
            if (!tryInstallDescriptor(index2, expected2)) return false
            return true
        }

        private fun tryInstallDescriptor(index: Int, expected: E): Boolean {
            while (true) {
                when (val item = array[index].value) {
                    this -> {
                        return true // already installed
                    }
                    is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor -> {
                        item.apply()
                    }
                    expected -> {
                        if (array[index].compareAndSet(expected, this)) {
                            return true
                        }
                        else {
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
            if (status.value === SUCCESS) {
                array[index1].compareAndSet(this, update1)
                array[index2].compareAndSet(this, update2)
            }
            else {
                array[index1].compareAndSet(this, expected1)
                array[index2].compareAndSet(this, expected2)
            }
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}