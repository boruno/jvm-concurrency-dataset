//package day3

import kotlinx.atomicfu.*
import AtomicArrayWithDCSS.Status.*

// This implementation never stores `null` values.
class AtomicArrayWithDCSS<E : Any>(size: Int, initialValue: E) {
    private val array = atomicArrayOfNulls<DCSSDescriptor>(size)

    init {
        // Fill array with the initial value.
        for (i in 0 until size) {
            array[i].value = DCSSDescriptor(i, initialValue, initialValue, 0, 0).apply {
                status.value = SUCCESS
            }
        }
    }

    fun get(index: Int): E = array[index].value!!.read() as E

    fun cas(index: Int, expected: E, update: E): Boolean {
        val desc = DCSSDescriptor(index, expected, update, 0, UNIVERSAL)
        desc.apply()
        return desc.status.value == SUCCESS
    }

    fun dcss(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
        val desc = DCSSDescriptor(index1, expected1, update1, index2, expected2)
        desc.apply()
        return desc.status.value == SUCCESS
    }

    inner class DCSSDescriptor(
        private val index1: Int,
        private val expected1: Any,
        private val update: Any,
        private val index2: Int,
        private val expected2: Any
    ) {
        val status = atomic(UNDECIDED)

        fun read(): Any {
            return if (status.value == SUCCESS) update else expected1
        }

        fun apply() {
            if (status.value != UNDECIDED) return
            if (installOrHelp()) {
                if (expected2 === UNIVERSAL || array[index2].value === expected2) {
                    status.compareAndSet(UNDECIDED, SUCCESS)
                } else {
                    status.compareAndSet(UNDECIDED, FAILED)
                }
            }
        }

        private fun installOrHelp(): Boolean {
            while (true) {
                if (status.value != UNDECIDED) return false
                val current = array[index1].value!!
                if (current === this) return true
                if (current.status.value != UNDECIDED) {
                    current.apply()
                }

                if (current.read() != expected1) {
                    status.compareAndSet(UNDECIDED, FAILED)
                    return false
                }

                if (array[index1].compareAndSet(current, this)) {
                    return true
                }
            }
        }

    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}

private val UNIVERSAL = Any()