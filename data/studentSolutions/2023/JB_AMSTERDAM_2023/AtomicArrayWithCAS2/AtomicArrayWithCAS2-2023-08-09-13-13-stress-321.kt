@file:Suppress("DuplicatedCode", "UNCHECKED_CAST")

package day3

import day3.AtomicArrayWithCAS2.Status.*
import java.util.concurrent.atomic.*

// This implementation never stores `null` values.
class AtomicArrayWithCAS2<E : Any>(size: Int, initialValue: E) {
    private val array = AtomicReferenceArray<Any?>(size)

    init {
        // Fill array with the initial value.
        for (i in 0 until size) {
            array[i] = initialValue
        }
    }

    fun get(index: Int): E? {
        // TODO: the cell can store a descriptor
        val content = array[index]
        if (content is AtomicArrayWithCAS2<*>.CAS2Descriptor) {
            val status = content.status.get()
            return (if (status == SUCCESS) {
                if (content.index1 == index) content.update1 else content.update2
            } else {
                if (content.index1 == index) content.expected1 else content.expected2
            }) as E
        } else {
            return content as E
        }
    }

    fun cas(index: Int, expected: E?, update: E?): Boolean {
        // TODO: the cell can store a descriptor
        return array.compareAndSet(index, expected, update)
    }

    fun cas2(
        index1: Int, expected1: E?, update1: E?,
        index2: Int, expected2: E?, update2: E?
    ): Boolean {
//        require(index1 != index2) { "The indices should be different" }
//        // TODO: this implementation is not linearizable,
//        // TODO: Store a CAS2 descriptor in array[index1].
//        if (array[index1] != expected1 || array[index2] != expected2) return false
//        array[index1] = update1
//        array[index2] = update2
//        return true

        require(index1 != index2) { "The indices should be different" }

        expected1!!
        expected2!!
        update1!!
        update2!!

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
        val index1: Int,
        val expected1: E,
        val update1: E,
        val index2: Int,
        val expected2: E,
        val update2: E
    ) {
        val status = AtomicReference(UNDECIDED)

        fun apply() {
            // TODO: Install the descriptor, update the status, and update the cells;
            // TODO: create functions for each of these three phases.

            while (true) {
                if (status.get() != UNDECIDED) break

                val content = array[index1]
                if (content == this) break

                if (content == expected1) {
                    if (array.compareAndSet(index1, expected1, this)) break
                    else continue
                }

                if (content is AtomicArrayWithCAS2<*>.CAS2Descriptor) {
                    content.apply()
                    continue
                }

                status.compareAndSet(UNDECIDED, FAILED)
                break
            }

            while (true) {
                if (status.get() != UNDECIDED) break

                val content = array[index2]
                if (content == this) break

                if (content == expected2) {
                    if (array.compareAndSet(index2, expected2, this)) break
                    else continue
                }

                if (content is AtomicArrayWithCAS2<*>.CAS2Descriptor) {
                    content.apply()
                    continue
                }

                status.compareAndSet(UNDECIDED, FAILED)
                break
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