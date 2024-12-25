@file:Suppress("DuplicatedCode")

//package day3

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
        // TODO: the cell can store a descriptor
//        return array[index].value as E?
        val value = array[index].value
        return when (value) {
            is AtomicArrayWithCAS2<*>.CAS2Descriptor -> return when (value.status.value) {
                SUCCESS -> if (index == value.index1) value.update1   as E else value.update2   as E
                else ->    if (index == value.index1) value.expected1 as E else value.expected2 as E
            }
            is AtomicArrayWithCAS2<*>.DCSSDescriptor -> return when (value.status.value) {
                SUCCESS -> {
                    val update1 = value.update1 as AtomicArrayWithCAS2<*>.CAS2Descriptor
                    return when (update1.status.value) {
                        SUCCESS -> if (index == update1.index1) update1.update1   as E else update1.update2   as E
                        else ->    if (index == update1.index1) update1.expected1 as E else update1.expected2 as E
                    }
                }
                else ->    value.expected1 as E
            }
            else -> value as E
        }
    }

    fun cas(index: Int, expected: E?, update: E?): Boolean {
        // TODO: the cell can store a descriptor
//        return array[index].compareAndSet(expected, update)
        while (!array[index].compareAndSet(expected, update)) {
            val actual = array[index].value
            if (actual is AtomicArrayWithCAS2<*>.DCSSDescriptor) {
                if (actual.status.value == UNDECIDED)
                    actual.apply()
                else
                    actual.updateCellsSuccessOrFailed()
                continue
            }

            if (actual != expected)
                return false
        }

        return true
    }

    fun cas2(
        index1: Int, expected1: E?, update1: E?,
        index2: Int, expected2: E?, update2: E?
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
        // TODO: this implementation is not linearizable,
        // TODO: Store a CAS2 descriptor in array[index1].
//        if (array[index1].value != expected1 || array[index2].value != expected2) return false
//        array[index1].value = update1
//        array[index2].value = update2
//        return true
        val descriptor = CAS2Descriptor(
            index1 = index1, expected1 = expected1!!, update1 = update1!!,
            index2 = index2, expected2 = expected2!!, update2 = update2!!
        )
        descriptor.apply()
        return descriptor.status.value === SUCCESS
    }

    fun dcss(
        index1: Int, expected1: Any, update1: Any,
        cas2DescriptorStatus2: CAS2Descriptor,
    ): Boolean {
        val descriptor = DCSSDescriptor(
            index1 = index1, expected1 = expected1, update1 = update1,
            cas2DescriptorStatus2 = cas2DescriptorStatus2
        )

        while (!array[index1].compareAndSet(expected1, descriptor)) {
            val actual1 = array[index1].value
            if (actual1 is AtomicArrayWithCAS2<*>.DCSSDescriptor) {
                actual1.apply()
            }
            else if (actual1 != expected1) {
                return false
            }
        }
        descriptor.apply()
        return descriptor.status.value === SUCCESS
    }

    inner class CAS2Descriptor(
        val index1: Int,
        val expected1: E,
        val update1: E,
        val index2: Int,
        val expected2: E,
        val update2: E
    ) {
        val status = atomic(UNDECIDED)

        fun apply() {
            // TODO: Install the descriptor, update the status, and update the cells;
            // TODO: create functions for each of these three phases.
            val (firstIndex, secondIndex) = if (index1 < index2) index1 to index2 else index2 to index1
            val (firstExpected, secondExpected) = if (index1 < index2) expected1 to expected2 else expected2 to expected1

            while (!dcss(firstIndex, firstExpected, this, this)) {
                val firstActual = array[firstIndex].value
                if (firstActual is AtomicArrayWithCAS2<*>.CAS2Descriptor) {
                    if (firstActual == this) break
                    if (firstActual.status.value == UNDECIDED)
                        firstActual.apply()
                    else
                        firstActual.updateCellsSuccessOrFailed()
                }
                else if (firstActual != firstExpected) {
                    status.compareAndSet(UNDECIDED, FAILED)
                    updateCellsSuccessOrFailed()
                    return
                }
                else if (status.value != UNDECIDED) {
                    updateCellsSuccessOrFailed()
                    return
                }
            }

            while (!dcss(secondIndex, secondExpected, this, this)) {
                val secondActual = array[secondIndex].value
                if (secondActual is AtomicArrayWithCAS2<*>.CAS2Descriptor) {
                    if (secondActual == this) break
                    if (secondActual.status.value == UNDECIDED)
                        secondActual.apply()
                    else
                        secondActual.updateCellsSuccessOrFailed()
                }
                else if (secondActual != secondExpected) {
                    status.compareAndSet(UNDECIDED, FAILED)
                    updateCellsSuccessOrFailed()
                    return
                }
                else if (status.value != UNDECIDED) {
                    updateCellsSuccessOrFailed()
                    return
                }
            }

            status.compareAndSet(UNDECIDED, SUCCESS)
            updateCellsSuccessOrFailed()
        }

        private fun updateCellsSuccessOrFailed() {
            check(status.value == SUCCESS || status.value == FAILED)
            if (status.value == SUCCESS) {
                array[index1].compareAndSet(this, update1)
                array[index2].compareAndSet(this, update2)
            } else {
                array[index1].compareAndSet(this, expected1)
                array[index2].compareAndSet(this, expected2)
            }
        }
    }

    inner class DCSSDescriptor(val index1: Int,
                               val expected1: Any,
                               val update1: Any,
                               val cas2DescriptorStatus2: CAS2Descriptor) {
        val status = atomic(UNDECIDED)

        fun apply() {
            if (cas2DescriptorStatus2.status.value != UNDECIDED) {
                status.compareAndSet(UNDECIDED, FAILED)
            } else {
                status.compareAndSet(UNDECIDED, SUCCESS)
            }

            updateCellsSuccessOrFailed()
        }

        fun updateCellsSuccessOrFailed() {
            check(status.value == SUCCESS || status.value == FAILED)
            if (status.value == SUCCESS) {
                array[index1].compareAndSet(this, update1)
            } else {
                array[index1].compareAndSet(this, expected1)
            }
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}