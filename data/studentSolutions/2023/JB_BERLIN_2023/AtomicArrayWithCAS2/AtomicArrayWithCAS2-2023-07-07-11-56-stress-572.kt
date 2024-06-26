@file:Suppress("DuplicatedCode")

package day3

import day3.AtomicArrayWithCAS2.Status.*
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls

// This implementation never stores `null` values.
class AtomicArrayWithCAS2<E : Any>(size: Int, initialValue: E) {
    private val array = atomicArrayOfNulls<Descriptor>(size)

    init {
        // Fill array with the initial value.
        for (i in 0 until size) {
            array[i].value = Descriptor(i, initialValue, initialValue, i, initialValue, initialValue).apply {
                status.value = SUCCESS
            }
        }
    }

    fun get(index: Int): E {
        val desc = array[index].value!!
        return desc.read(index) as E
    }

    fun cas(index: Int, expected: E, update: E): Boolean {
        val descriptor = Descriptor(-1, null, null, index, expected, update)
        descriptor.apply()
        return descriptor.status.value == SUCCESS
    }

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }

        val descriptor = if (index1 < index2) Descriptor(index1, expected1, update1, index2, expected2, update2)
        else Descriptor(index2, expected2, update2, index1, expected1, update1)

        descriptor.apply()
        return descriptor.status.value == SUCCESS
    }

    private inner class Descriptor(
        private val index1: Int,
        private val expected1: Any?,
        private val update1: Any?,
        private val index2: Int,
        private val expected2: Any,
        private val update2: Any
    ) {
        val status = atomic(UNDECIDED)

        fun read(index: Int): Any {
            check(index == index1 || index == index2)
            return if (status.value == SUCCESS) {
                if (index == index1) update1 else update2
            } else {
                if (index == index1) expected1 else expected2
            }!!
        }

        fun apply(initial: Boolean= true) {
            if (status.value == UNDECIDED) {
                val install1 = if (initial || index1 == -1) true else installOrHelp(true)
                val install2 = installOrHelp(false)

                if (install1 && install2) {
                    status.compareAndSet(UNDECIDED, SUCCESS)
                }
            }
        }

        private fun installOrHelp(first: Boolean): Boolean {
            val index = if (first) index1 else index2
            val expected = if (first) expected1 else expected2
            check(index != -1)
            while (true) {
                if (status.value != UNDECIDED) return false
                val current = array[index].value!!
                if (current === this) return true
                current.apply(false)
                if (current.read(index) !== expected) {
                    status.compareAndSet(UNDECIDED, FAILED)
                    return false
                } else if (array[index].compareAndSet(current, this)) {
                    return true
                }
            }
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }

}
