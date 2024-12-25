@file:Suppress("DuplicatedCode", "UNCHECKED_CAST")

//package day3

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
        val descriptor = CAS2Descriptor(
            index1 = index1, expected1 = expected1, update1 = update1,
            index2 = index2, expected2 = expected2, update2 = update2
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

        private fun helpToProcessDescriptor(anotherDescriptor: CAS2Descriptor): Boolean {
            val indexA = Math.min(anotherDescriptor.index1, anotherDescriptor.index2)
            val indexB = Math.max(anotherDescriptor.index1, anotherDescriptor.index2)
            val expectedA = if (indexA == anotherDescriptor.index1) anotherDescriptor.expected1 else anotherDescriptor.expected2
            val expectedB = if (indexB == anotherDescriptor.index1) anotherDescriptor.expected1 else anotherDescriptor.expected2

            val status = anotherDescriptor.status.get()

            if (status != SUCCESS && !array.compareAndSet(indexA, expectedA, anotherDescriptor))
                if (array.get(indexA) != anotherDescriptor) {
                    anotherDescriptor.updateStatus(FAILED)
                    return false
                }
            if (status != SUCCESS && !array.compareAndSet(indexB, expectedB, anotherDescriptor))
                if (array.get(indexB) != anotherDescriptor) {
                    anotherDescriptor.updateStatus(FAILED)
                    array.compareAndSet(indexA, anotherDescriptor, expectedA)
                    return false
                }

            if (status == SUCCESS || anotherDescriptor.status.compareAndSet(UNDECIDED, SUCCESS)) {
                anotherDescriptor.updateCells()
                return true
            }
            return false
        }

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
                        val result = updateStatus(SUCCESS)
                        if (!result)
                            array.compareAndSet(indexB, this, expectedB)
                        return result
                    } else {
                        val v = array.get(indexB)
                        if (v is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
                            //todo(vg): need to help with B!!!
                            helpToProcessDescriptor(v as AtomicArrayWithCAS2Simplified<E>.CAS2Descriptor)
                        } else {
                            //note(vg): we have failed to install the descriptor, need to roll back the change in the cell #1
                            updateStatus(FAILED) //note(vg): should we handle specifically the case when another thread helped us???
                            array.compareAndSet(indexA, this, expectedA) //note(vg): should we handle specifically the case when another thread helped us???
                            return false
                        }
                    }
                } else {
                    val v = array.get(indexA)
                    if (v is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
                        //todo(vg): need to help with A!!!
                        helpToProcessDescriptor(v as AtomicArrayWithCAS2Simplified<E>.CAS2Descriptor)
                    } else if (v == expectedA) {
                        continue
                    } else {
                        return false
                    }
                }
            }

//            while (true) {
//                if (array.compareAndSet(indexA, expectedA, this)) {
//                    if (array.compareAndSet(indexB, expectedB, this)) {
//                        updateStatus(SUCCESS) //note(vg): should we handle specifically the case when another thread helped us???
//                        return true //note(vg): we have installed the descriptor successfully
//                    } else {
//                        //note(vg): we have failed to install the descriptor, need to roll back the change in the cell #1
//                        updateStatus(FAILED) //note(vg): should we handle specifically the case when another thread helped us???
//                        array.compareAndSet(indexA, this, expectedA) //note(vg): should we handle specifically the case when another thread helped us???
//                        return false
//                    }
//                } else {
//                    //note(vg): there can be some other descriptor, in this case we should help
//                    val v = array.get(indexA)
//                    if (v is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
//                        //note(vg): @v processing can be in ANY state here! We should only continue it, not try from the beginning!!!
//
//                        if (v.status.get() != SUCCESS)
//                            v.apply()
//                        else
//                            v.updateCells()
//                    } else {
//                        return false
//                    }
//                }
//            }
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