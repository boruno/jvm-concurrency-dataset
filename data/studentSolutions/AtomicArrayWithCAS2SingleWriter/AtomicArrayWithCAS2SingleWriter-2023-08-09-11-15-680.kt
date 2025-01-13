@file:Suppress("DuplicatedCode", "UNCHECKED_CAST")

//package day3

import AtomicArrayWithCAS2SingleWriter.Status.*
import java.util.concurrent.atomic.*
import kotlin.reflect.KClass

// This implementation never stores `null` values.
class AtomicArrayWithCAS2SingleWriter<E : Any>(size: Int, initialValue: E) {
    private val array = AtomicReferenceArray<Any?>(size)

    init {
        // Fill array with the initial value.
        for (i in 0 until size) {
            array[i] = initialValue
        }
    }

    fun get(index: Int): E {
        // TODO: the cell can store CAS2Descriptor
        val value = array[index]
        if (value is AtomicArrayWithCAS2SingleWriter<*>.CAS2Descriptor) {
            val descriptor = array[index] as AtomicArrayWithCAS2SingleWriter<*>.CAS2Descriptor
            if (index == descriptor.index1){
                if (descriptor.status.get() != SUCCESS){
                    return descriptor.expected1 as E
                } else {
                    return descriptor.update1 as E
                }
            } else {
                if (descriptor.status.get() != SUCCESS){
                    return descriptor.expected2 as E
                } else {
                    return descriptor.update2 as E
                }
            }
        } else {
            return value as E
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
        val index1: Int, val expected1: E, val update1: E,
        val index2: Int, val expected2: E, val update2: E
    ) {
        val status = AtomicReference(UNDECIDED)

        fun apply() {
            if (array.compareAndSet(index1, expected1, this) &&
                array.compareAndSet(index2, expected2, this)) {
                status.compareAndSet(UNDECIDED, SUCCESS)
            } else {
                status.compareAndSet(UNDECIDED, FAILED)
            }
            if (status.get() == SUCCESS) {
                if (!array.compareAndSet(index1, this, update1)){
                    println("Success Failed for index1")
                }
                if (!array.compareAndSet(index2, this, update2)){
                    println("Success Failed for index2")
                }
            } else {
                array.compareAndSet(index1, this, expected1)
                array.compareAndSet(index2, this, expected2)
            }
            // TODO: Install the descriptor, update the status, and update the cells;
            // TODO: create functions for each of these three phases.
            // TODO: In this task, only one thread can call cas2(..),
            // TODO: so cas2(..) calls cannot be executed concurrently.
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}