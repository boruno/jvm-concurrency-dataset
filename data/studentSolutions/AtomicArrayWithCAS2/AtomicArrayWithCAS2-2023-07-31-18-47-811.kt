@file:Suppress("DuplicatedCode")

//package day4

import kotlinx.atomicfu.*
import kotlin.math.max
import kotlin.math.min

// This implementation never stores `null` values.
class AtomicArrayWithCAS2<E : Any>(size: Int, initialValue: E) {
    private val array = atomicArrayOfNulls<Any>(size)

    init {
        // Fill array with the initial value.
        for (i in 0 until size) {
            array[i].value = initialValue
        }
    }

    fun get(index: Int): E? {
        // TODO: the cell can store a descriptor
        while (true) {
            val curValue = array[index].value

            return if (curValue is AtomicArrayWithCAS2<*>.DCSSDescriptor) {
                curValue.apply()
                continue
            } else if (curValue is AtomicArrayWithCAS2<*>.CAS2Descriptor) {
                if (curValue.status.value == Status.SUCCESS) {
                    if (index == curValue.index1) curValue.update1 as E?
                    else curValue.update2 as E?
                } else {
                    if (index == curValue.index1) curValue.expected1 as E?
                    else curValue.expected2 as E?
                }
            } else {
                curValue as E?
            }
        }
    }

    fun cas(index: Int, expected: E?, update: E?): Boolean {
        // TODO: the cell can store a descriptor
        return anyCas(index, expected, update)
    }

    private fun anyCas(index: Int, expected: Any?, update: Any?): Boolean {
        // TODO: the cell can store a descriptor
        while (true) {
            return if (array[index].compareAndSet(expected, update)) {
                true
            } else {
                val curValue = array[index].value

                if (curValue is AtomicArrayWithCAS2<*>.DCSSDescriptor) {
                    curValue.apply()
                    continue
                } else if (curValue is AtomicArrayWithCAS2<*>.CAS2Descriptor) {
                    curValue.applyOperation()
                    continue
                } else if (curValue == expected) {
                    continue
                } else {
                    false
                }
            }
        }
    }

    private fun dcss(
        index1: Int, expected1: Any?, update1: Any?,
        index2: CAS2Descriptor, expected2: Status
    ): Boolean {
        val descriptor = DCSSDescriptor(index1, expected1, update1, index2, expected2)

        if (!anyCas(index1, expected1, descriptor)) {
            return false
        }

        descriptor.apply()

        return descriptor.status.value == Status.SUCCESS
    }

    fun cas2(
        index1_: Int, expected1_: E?, update1_: E?,
        index2_: Int, expected2_: E?, update2_: E?
    ): Boolean {
        require(index1_ != index2_) { "The indices should be different" }

        val index1 = min(index1_, index2_)
        val index2 = max(index1_, index2_)

        val expected1 = if (index1 == index1_) expected1_ else expected2_
        val expected2 = if (index2 == index2_) expected2_ else expected1_

        val update1 = if (index1 == index1_) update1_ else update2_
        val update2 = if (index2 == index2_) update2_ else update1_

        val descriptor = CAS2Descriptor(index1, expected1, update1, index2, expected2, update2)

        return if (anyCas(index1, expected1, descriptor)) {
            descriptor.applyOperation()

            descriptor.status.value == Status.SUCCESS
        } else {
            false
        }
    }

    private inner class CAS2Descriptor(
        val index1: Int, val expected1: E?, val update1: E?,
        val index2: Int, val expected2: E?, val update2: E?
    ) {
        val status = atomic(Status.UNDECIDED)

        // TODO: Other threads can call this function
        // TODO: to help completing the operation.
        fun applyOperation() {
            // TODO: Use the CAS2 algorithm, installing this descriptor
            // TODO: in `array[index1]` and `array[index2]` cells.
            when (val curStatus = status.value) {
                Status.SUCCESS -> {
                    array[index1].compareAndSet(this, update1)
                    array[index2].compareAndSet(this, update2)
                }
                Status.FAILED -> {
                    array[index1].compareAndSet(this, expected1)
                    array[index2].compareAndSet(this, expected2)
                }
                Status.UNDECIDED -> {
                    val curValue2 = array[index2].value

                    if (curValue2 == expected2) {
                        if (dcss(index2, curValue2, this, this, Status.UNDECIDED)) {
                            status.compareAndSet(curStatus, Status.SUCCESS)
                        }
                    } else if (curValue2 == this) {
                        status.compareAndSet(curStatus, Status.SUCCESS)
                    } else {
                        status.compareAndSet(curStatus, Status.FAILED)
                    }

                    applyOperation()
                }
            }
        }
    }

    private inner class DCSSDescriptor(
        val index1: Int, val expected1: Any?, val update1: Any?,
        val index2: CAS2Descriptor, val expected2: Status
    ) {
        val status = atomic(Status.UNDECIDED)

        fun apply() {
            when (val curStatus = status.value) {
                Status.UNDECIDED -> {
                    if (index2.status.value == expected2) {
                        status.compareAndSet(curStatus, Status.SUCCESS)
                    } else {
                        status.compareAndSet(curStatus, Status.FAILED)
                    }

                    apply()
                }
                Status.SUCCESS -> {
                    array[index1].compareAndSet(this, update1)
                }
                Status.FAILED -> {
                    array[index1].compareAndSet(this, expected1)
                }
            }
        }
    }

    private enum class Status {
        UNDECIDED, FAILED, SUCCESS
    }
}