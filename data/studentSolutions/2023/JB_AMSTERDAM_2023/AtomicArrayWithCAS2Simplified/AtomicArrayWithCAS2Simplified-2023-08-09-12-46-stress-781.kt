@file:Suppress("DuplicatedCode", "UNCHECKED_CAST")

package day3

import day3.AtomicArrayWithCAS2Simplified.Status.*
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

    @Suppress("UNREACHABLE_CODE")
    fun get(index: Int): E {
        // TODO: the cell can store CAS2Descriptor
        val v = array.get(index)
        if (v is AtomicArrayWithCAS2SingleWriter<*>.CAS2Descriptor)
            return when (v.status.get()!!) {
                AtomicArrayWithCAS2SingleWriter.Status.UNDECIDED, AtomicArrayWithCAS2SingleWriter.Status.FAILED ->
                    when (index) {
                        v.index1 -> return v.expected1 as E
                        v.index2 -> return v.expected2 as E
                        else -> throw IllegalArgumentException()
                    }
                AtomicArrayWithCAS2SingleWriter.Status.SUCCESS ->
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

        private fun installDescriptor(): Boolean {
            val indexA = Math.min(index1, index2)
            val indexB = Math.max(index1, index2)
            val expectedA = if (indexA == index1) expected1 else expected2
            val expectedB = if (indexB == index1) expected1 else expected2
//            val updateA = if (indexA == index1) update1 else update2
//            val updateB = if (indexB == index1) update1 else update2

            while (true) {
                if (array.compareAndSet(indexA, expectedA, this)) {
                    if (array.compareAndSet(indexB, expectedB, this)) {
                        updateStatus(SUCCESS) //note(vg): should we handle specifically the case when another thread helped us???
                        return true //note(vg): we have installed the descriptor successfully
                    } else {
                        //note(vg): we have failed to install the descriptor, need to roll back the change in the cell #1
                        updateStatus(FAILED) //note(vg): should we handle specifically the case when another thread helped us???
                        array.compareAndSet(indexA, this, expectedA) //note(vg): should we handle specifically the case when another thread helped us???
                        return false
                    }
                } else {
                    //note(vg): there can be some other descriptor, in this case we should help
                    val v = array.get(indexA)
                    if (v is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
                        if (v.status.get() != SUCCESS)
                            v.apply()
                        else
                            v.updateCells()
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