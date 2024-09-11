package day3

import day3.AtomicArrayWithDCSS.Status.*
import kotlinx.atomicfu.*
import kotlin.math.max
import kotlin.math.min

// This implementation never stores `null` values.
class AtomicArrayWithDCSS<E : Any>(size: Int, initialValue: E) {
    private val array = atomicArrayOfNulls<Any?>(size)

    init {
        // Fill array with the initial value.
        for (i in 0 until size) {
            array[i].value = initialValue
        }
    }

    fun get(index: Int): E {
        val value = array[index].value
        if (value is AtomicArrayWithDCSS<*>.DcssDescriptor) {
            return value.get(index) as E
        }
        // TODO: the cell can store a descriptor
        return value as E
    }

    fun cas(index: Int, expected: E?, update: E?): Boolean {
        // TODO: the cell can store a descriptor
        while (true) {
            when (val value = array[index].value) {
                is AtomicArrayWithDCSS<*>.DcssDescriptor -> value.receiveHelp(index)
                expected -> return array[index].compareAndSet(expected, update)
                else -> return false
            }
        }
//        return array[index].compareAndSet(expected, update)
    }

    fun dcss(
        index1: Int, expected1: E?, update1: E?,
        index2: Int, expected2: E?
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
        // TODO This implementation is not linearizable!
        // TODO Store a DCSS descriptor in array[index1].
        val descriptor = DcssDescriptor(
            index1, expected1, update1, index2, expected2
        )
        descriptor.apply()
        return descriptor.status.value === SUCCESS
    }

    private inner class DcssDescriptor(
        val index1: Int,
        val expected1: E?,
        val update1: E?,
        val index2: Int,
        val expected2: E?,
    ) {
        val status = atomic(UNDECIDED)

        fun get(index: Int): E {
            if (index1 != index) return array[index].value as E
            return if (status.value == SUCCESS) {
                update1
            } else {
                expected1
            } as E
        }

        fun receiveHelp(index: Int) {
            when (status.value) {
                UNDECIDED -> {
                    val result = if (index1 == index) {
                        array[index2].value == expected2
                    } else {
                        array[index1].value == expected1
                    }
                    if (result) {
                        status.compareAndSet(UNDECIDED, SUCCESS)
                    }
                    else {
                        status.compareAndSet(UNDECIDED, FAILED)
                    }
                }
                SUCCESS -> {
                    array[index1].compareAndSet(this, update1)
                }
                FAILED -> {
                    array[index1].compareAndSet(this, expected1)
                }
            }
        }

        fun apply() {
            val minIndex = min(index1, index2)
            val maxIndex = max(index1, index2)
            val minExpected = if (minIndex == index1) expected1 else expected2
            val maxExpected = if (minIndex == index1) expected2 else expected1

            while (true) {
                when (val firstElement = array[minIndex].value) {
                    minExpected -> {
                        if (array[minIndex].compareAndSet(minExpected, this)) break
                    }
                    is AtomicArrayWithDCSS<*>.DcssDescriptor -> {
                        firstElement.receiveHelp(minIndex)
                    }
                    else -> {
                        status.compareAndSet(UNDECIDED, FAILED)
                        return
                    }
                }
            }

            while (true) {
                when (val secondElement = array[maxIndex].value) {
                    maxExpected -> {
                        if (status.compareAndSet(UNDECIDED, SUCCESS)) {
                            array[minIndex].compareAndSet(this, update1)
                            return
                        }
                    }
                    is AtomicArrayWithDCSS<*>.DcssDescriptor -> {
                        secondElement.receiveHelp(maxIndex)
                    }
                    else -> {
                        if (status.compareAndSet(UNDECIDED, FAILED)) {
                            array[minIndex].compareAndSet(this, minExpected)
                            return
                        }
                    }
                }
            }
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}