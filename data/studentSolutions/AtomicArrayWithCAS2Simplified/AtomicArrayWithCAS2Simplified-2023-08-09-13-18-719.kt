@file:Suppress("DuplicatedCode", "UNCHECKED_CAST")

//package day3

import AtomicArrayWithCAS2Simplified.Status.*
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
        val value = array[index]
        return if (value is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
            when(value.status.get()) {
                UNDECIDED, FAILED -> value.getExpectedForIndex(index) as E
                SUCCESS -> value.getUpdateForIndex(index) as E
                else -> throw IllegalStateException()
            }
        } else {
            value as E
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
        private val index1: Int,
        private val expected1: E,
        private val update1: E,
        private val index2: Int,
        private val expected2: E,
        private val update2: E
    ) {
        val status = AtomicReference(UNDECIDED)

        fun apply() {
            // TODO: Install the descriptor, update the status, and update the cells;
            // TODO: create functions for each of these three phases.
            installDescriptor()
            updateStatus()
            updateCells()
        }

        private val sortedDesc: List<Triple<Int, E, E>>
            get() = setOf(
                Triple(index1, expected1, update1),
                Triple(index2, expected2, update2)
            ).sortedWith { p0, p1 ->
                p0.first.compareTo(p1.first)
            }

        private fun installDescriptor() {
            while (true) {
                sortedDesc.forEachIndexed { index, triple ->
                    val value = try {
                        array[triple.first]
                    } catch (e: IndexOutOfBoundsException) {
                        System.err.println("ZZZ: $triple")
                        throw e
                    }
                    if (value == this || array.compareAndSet(triple.first, triple.second, this)) {
                        if (index == sortedDesc.lastIndex) status.compareAndSet(UNDECIDED, SUCCESS)
                    } else {
                        val arrayIndexValue = array[triple.first]
                        if (arrayIndexValue is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
                            arrayIndexValue.updateCells()
                            return@forEachIndexed
                        } else {
                            status.compareAndSet(UNDECIDED, FAILED)
                            return
                        }
                    }
                }
//                if (array[index1] == this || array.compareAndSet(index1, expected1, this)) {
//                    if (array.compareAndSet(index2, expected2, this)) {
//                        status.compareAndSet(UNDECIDED, SUCCESS)
//                    } else {
//                        val arrayIndex2 = array[index2]
//                        if (arrayIndex2 is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
//                            arrayIndex2.updateCells()
//                        } else {
//                            status.compareAndSet(UNDECIDED, FAILED)
//                            break
//                        }
//                    }
//                } else {
//                    val arrayIndex1 = array[index1]
//                    if (arrayIndex1 is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
//                        arrayIndex1.updateCells()
//                    } else {
//                        status.compareAndSet(UNDECIDED, FAILED)
//                        break
//                    }
//                }
            }
        }

        private fun updateStatus() {

        }

        private fun updateCells() {
            when (status.get()) {
                FAILED -> {
                    array.compareAndSet(index1, this, expected1)
                    array.compareAndSet(index2, this, expected2)
                }
                SUCCESS -> {
                    array.compareAndSet(index1, this, update1)
                    array.compareAndSet(index2, this, update2)
                }
                else -> Unit // UNDECIDED and eventually will be updated
            }
        }

        fun getExpectedForIndex(index: Int): E  {
            return when (index) {
                index1 -> expected1
                index2 -> expected2
                else -> throw IllegalArgumentException("Current CAS2 operation does not know $index")
            }
        }

        fun getUpdateForIndex(index: Int): E  {
            return when (index) {
                index1 -> update1
                index2 -> update2
                else -> throw IllegalArgumentException("Current CAS2 operation does not know $index")
            }
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}