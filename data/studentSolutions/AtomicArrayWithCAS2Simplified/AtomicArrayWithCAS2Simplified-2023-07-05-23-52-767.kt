//package day3

import day3.AtomicArrayWithCAS2Simplified.Status.*
import kotlinx.atomicfu.*


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
        array[index].loop {
            if (it is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
                it.apply()
            } else {
                return it as E
            }
        }
    }

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }

        val desc = if (index1 < index2) {
            CAS2Descriptor(index1, expected1, update1, index2, expected2, update2)
        } else {
            CAS2Descriptor(index2, expected2, update2, index1, expected1, update1)
        }
        desc.apply()

        return desc.status.value == SUCCESS
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
            if (tryInstall()) {
                status.compareAndSet(UNDECIDED, SUCCESS)
            } else {
                status.compareAndSet(UNDECIDED, FAILED)
            }
            if (status.value === SUCCESS) {
                array[index1].compareAndSet(this, update1)
                array[index2].compareAndSet(this, update2)
            } else {
                array[index1].compareAndSet(this, expected1)
                array[index2].compareAndSet(this, expected2)
            }
        }

        private fun tryInstall(): Boolean {
            if (!tryInstallInCell(index1, expected1)) return false
            if (!tryInstallInCell(index2, expected2)) return false
            return true
        }

        private fun tryInstallInCell(index: Int, expected: E): Boolean {
            while (true) {
                val cur = array[index].value
                when {
                    cur === this -> {
                        return true
                    }
                    cur is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor -> {
                        array[index].value
                        cur.apply()
                    }
                    else -> {
                        if (cur !== expected) return false
                        if (array[index].compareAndSet(expected, this)) return true
                    }
                }
            }
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}