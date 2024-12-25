//package day3

import day3.AtomicArrayWithDCSS.Status.*
import kotlinx.atomicfu.*


// This implementation never stores `null` values.
@Suppress("UNCHECKED_CAST")
class AtomicArrayWithDCSS<E : Any>(size: Int, initialValue: E) {
    private val array = atomicArrayOfNulls<Any?>(size)

    init {
        // Fill array with the initial value.
        for (i in 0 until size) {
            array[i].value = initialValue
        }
    }

    fun get(index: Int): E? {
        val value = array[index].value

        return if (value is AtomicArrayWithDCSS<*>.DCSSDescriptor) {
            if (value.status.value == SUCCESS) value.update1 as E? else value.expected1 as E?
        } else {
            value as E?
        }
    }

    fun cas(index: Int, expected: E?, update: E?): Boolean {
        // TODO: the cell can store a descriptor
        while (true) {
            val value = array[index].value

            if (value == expected) {
                if (array[index].compareAndSet(expected, update))
                    return true
                // Means that DSS was added
                else
                    continue
            } else if (value is AtomicArrayWithDCSS<*>.DCSSDescriptor) {
                value.apply()
                continue
            }
            return false
        }
    }

    fun dcss(
        index1: Int, expected1: E?, update1: E?,
        index2: Int, expected2: E?
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }

        // TODO This implementation is not linearizable!
        // TODO Store a DCSS descriptor in array[index1].

        val descriptor = DCSSDescriptor(index1, expected1, index2, expected2, update1)
        descriptor.apply()

        return descriptor.status.value === SUCCESS
    }

    inner class DCSSDescriptor(
        val index1: Int,
        val expected1: E?,
        val index2: Int,
        val expected2: E?,
        val update1: E?
    ) {
        val status = atomic(UNDECIDED)

        fun apply() {
            val installed = install()
            updateStatus(installed)
            updateCells()
        }

        private fun install(): Boolean {
            return array[index1].compareAndSet(expected1, this)
        }

        private fun updateStatus(installed: Boolean) {
            if (installed && array[index2].value == expected2) {
                this.status.compareAndSet(UNDECIDED, SUCCESS)
            } else {
                this.status.compareAndSet(UNDECIDED, FAILED)
            }
        }

        private fun updateCells() {
            if (this.status.value == SUCCESS) {
                array[index1].compareAndSet(this, update1)
            }
            if (this.status.value == FAILED) {
                array[index1].compareAndSet(this, expected1)
            }
        }

    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}