//package day3

import AtomicArrayWithCAS2Simplified.Status.*
import kotlinx.atomicfu.*
import kotlin.math.min


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
        val curVal = array[index].value
        val descriptor = curVal as? CAS2Descriptor<E> ?: return curVal as E
        return when (descriptor.status.value) {
            UNDECIDED, FAILED -> if (descriptor.index1 == index) descriptor.expected1 else descriptor.expected2
            SUCCESS -> if (descriptor.index1 == index) descriptor.update1 else descriptor.update2
        }
    }

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        // TODO: this implementation is not linearizable,
        // TODO: use CAS2Descriptor to fix it.
        require(index1 != index2) { "The indices should be different" }
        val descriptor = CAS2Descriptor(index1, expected1, update1, index2, expected2, update2)
        descriptor.apply()
        return descriptor.status.value == SUCCESS
    }

    fun CAS2Descriptor<E>.apply() {
        val from1 = min(index1, index2) == index1
        val firstIndex = if (from1) index1 else index2
        val firstExpected = if (from1) expected1 else expected2
        val secondIndex = if (from1) index2 else index1
        val secondExpected = if (from1) expected2 else expected1

        if (status.value == UNDECIDED) {
            val success = tryInstall(firstIndex, firstExpected) && tryInstall(secondIndex, secondExpected)
            if (success) status.compareAndSet(UNDECIDED, SUCCESS) else status.compareAndSet(UNDECIDED, FAILED)
        }
        updateValues()
    }

    private fun CAS2Descriptor<E>.updateValues() {
        if (status.value == SUCCESS) {
            array[index1].compareAndSet(expected1, update1)
            array[index2].compareAndSet(expected2, update2)
        } else if (status.value == FAILED) {
            array[index1].compareAndSet(this, expected1)
            array[index2].compareAndSet(this, expected2)
        }
    }

    private fun CAS2Descriptor<E>.tryInstall(firstIndex: Int, expected: E): Boolean {
        while (true) {
            when (val curState = array[firstIndex].value) {
                this -> return true
                is CAS2Descriptor<*> -> (curState as CAS2Descriptor<E>).apply()
                expected -> {
                    val wasExpected = array[firstIndex].compareAndSet(expected, this)
                    if (wasExpected) return true else continue
                }
                else -> return false
            }
        }
    }

    class CAS2Descriptor<E>(
        val index1: Int,
        val expected1: E,
        val update1: E,
        val index2: Int,
        val expected2: E,
        val update2: E
    ) {
        val status = atomic(UNDECIDED)
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}