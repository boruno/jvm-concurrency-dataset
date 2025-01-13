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
        // TODO: the cell can store CAS2Descriptor
//        return array[index] as E

        val content = array[index]
        if (content is AtomicArrayWithCAS2SingleWriter<*>.CAS2Descriptor) {
            val status = content.status.get()
            return (if (status == AtomicArrayWithCAS2SingleWriter.Status.SUCCESS) {
                if (content.index1 == index) content.update1 else content.update2
            } else {
                if (content.index1 == index) content.expected1 else content.expected2
            }) as E
        } else {
            return content as E
        }
    }

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }

        val descriptor = if (index1 < index2)  CAS2Descriptor(
            index1 = index1, expected1 = expected1, update1 = update1,
            index2 = index2, expected2 = expected2, update2 = update2
        ) else CAS2Descriptor(
            index1 = index2, expected1 = expected2, update1 = update2,
            index2 = index1, expected2 = expected1, update2 = update1
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

            if (status.get() != FAILED) {

                while (true) {
                    if (!array.compareAndSet(index1, expected1, this)) {
                        val content = array[index1]
                        if (content == this) break
                        if (content is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
                            content.apply()
                            continue
                        }
                        if (content == expected1) continue
                        status.compareAndSet(UNDECIDED, FAILED)
                        break
                    }
                }
            }

            if (status.get() != FAILED) {
                while (true) {
                    if (!array.compareAndSet(index2, expected2, this)) {
                        val content = array[index2]
                        if (content == this) break
                        if (content is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
                            content.apply()
                            continue
                        }
                        if (content == expected2) continue
                        status.compareAndSet(UNDECIDED, FAILED)
                        break
                    }
                }
            }


            if (array[index1] == this && array[index2] == this ) {
                status.compareAndSet(UNDECIDED, SUCCESS)
            }



            if (status.get() == SUCCESS) {
                array.compareAndSet(index1, this, update1)
                array.compareAndSet(index2, this, update2)
            } else {
                array.compareAndSet(index1, this, expected1)
                array.compareAndSet(index2, this, expected2)
            }
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}