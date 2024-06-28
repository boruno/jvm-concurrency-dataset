@file:Suppress("DuplicatedCode", "UNCHECKED_CAST")

package day3

import day3.AtomicArrayWithCAS2Simplified.Status.*
import java.util.concurrent.atomic.*
import kotlin.math.max
import kotlin.math.min


// This implementation never stores `null` values.
class AtomicArrayWithCAS2Simplified<E : Any>(size: Int, initialValue: E) {
    private val array = AtomicReferenceArray<Any?>(size)

    init {
        // Fill array with the initial value.
        for (i in 0 until size) {
            array[i] = initialValue
        }
    }

    @Suppress("UNREACHABLE_CODE")
    fun get(index: Int): E {
        // TODO: the cell can store CAS2Descriptor
        val v = array.get(index)
        if (v is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor)
            return when (v.status.get()!!) {
                UNDECIDED, FAILED ->
                    when (index) {
                        v.index1 -> return v.expected1 as E
                        v.index2 -> return v.expected2 as E
                        else -> throw IllegalArgumentException()
                    }
                SUCCESS ->
                    when (index) {
                        v.index1 -> return v.update1 as E
                        v.index2 -> return v.update2 as E
                        else -> throw IllegalArgumentException()
                    }
            }
        return v as E
    }

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
        val descriptor = if (index1 < index2) {
            CAS2Descriptor(
                index1 = index1, expected1 = expected1, update1 = update1,
                index2 = index2, expected2 = expected2, update2 = update2
            )
        } else {
            CAS2Descriptor(
                index1 = index2, expected1 = expected2, update1 = update2,
                index2 = index1, expected2 = expected1, update2 = update1
            )
        }
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

        private fun helpToProcessDescriptor(anotherDescriptor: CAS2Descriptor): Boolean {
            if (anotherDescriptor.status.get() != SUCCESS && !array.compareAndSet(index1, expected1, anotherDescriptor))
                if (array.get(index1) != anotherDescriptor) {
                    anotherDescriptor.updateStatus(FAILED)
                    return false
                }
            if (anotherDescriptor.status.get() != SUCCESS && !array.compareAndSet(index2, expected2, anotherDescriptor))
                if (array.get(index2) != anotherDescriptor) {
                    anotherDescriptor.updateStatus(FAILED)
                    array.compareAndSet(index1, anotherDescriptor, expected1)
                    return false
                }

            if (anotherDescriptor.status.get() == SUCCESS || anotherDescriptor.updateStatus(SUCCESS)) {
                anotherDescriptor.updateCells()
                return true
            } else {
                if (anotherDescriptor.status.get() == FAILED) {
                    array.compareAndSet(index1, anotherDescriptor, expected1)
                    array.compareAndSet(index2, anotherDescriptor, expected2)
                }
                return false
            }
        }

        private fun installDescriptor(): Boolean {
            while (true) {
                if (array.compareAndSet(index1, expected1, this) || array.get(index1) == this) {
                    if (array.compareAndSet(index2, expected2, this) || array.get(index2) == this) {
                        val result = updateStatus(SUCCESS)
                        if (!result && status.get() == FAILED)
                            array.compareAndSet(index2, this, expected2)
                        return result
                    } else {
                        val v = array.get(index2)
                        if (v is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
                            //todo(vg): need to help with B!!!
                            helpToProcessDescriptor(v as AtomicArrayWithCAS2Simplified<E>.CAS2Descriptor)
                        } else {
                            //note(vg): we have failed to install the descriptor, need to roll back the change in the cell #1
                            val result = updateStatus(FAILED) //note(vg): should we handle specifically the case when another thread helped us???
                            if (result || status.get() != SUCCESS)
                                array.compareAndSet(index1, this, expected1) //note(vg): should we handle specifically the case when another thread helped us???
                            return false
                        }
                    }
                } else {
                    val v = array.get(index1)
                    if (v is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
                        //todo(vg): need to help with A!!!
                        helpToProcessDescriptor(v as AtomicArrayWithCAS2Simplified<E>.CAS2Descriptor)
                    } else if (v == expected1) {
                        continue
                    } else {
                        return false
                    }
                }
            }
        }

        private fun updateStatus(newStatus: Status): Boolean {
            return status.compareAndSet(UNDECIDED, newStatus)
        }

        private fun updateCells() {
            array.compareAndSet(index1, this, update1)
            array.compareAndSet(index2, this, update2)
        }

        fun apply() {
            // TODO: Install the descriptor, update the status, and update the cells;
            // TODO: create functions for each of these three phases.
            if (installDescriptor())
                updateCells()
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}