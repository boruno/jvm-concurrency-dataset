@file:Suppress("DuplicatedCode", "UNCHECKED_CAST")

//package day3

import AtomicArrayWithCAS2AndImplementedDCSS.Status.*
import java.util.concurrent.atomic.*
import kotlin.math.max
import kotlin.math.min

// This implementation never stores `null` values.
class AtomicArrayWithCAS2AndImplementedDCSS<E : Any>(size: Int, initialValue: E) {
    private val array = AtomicReferenceArray<Any?>(size)

    init {
        // Fill array with the initial value.
        for (i in 0 until size) {
            array[i] = initialValue
        }
    }

    @Suppress("UNREACHABLE_CODE")
    fun get(index: Int): E {
        // TODO: Copy the implementation from `AtomicArrayWithCAS2Simplified`
        // TODO: the cell can store CAS2Descriptor
        val v = array.get(index)
        if (v is AtomicArrayWithCAS2AndImplementedDCSS<*>.CAS2Descriptor)
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

        private fun helpToProcess(): Boolean {
            if (dcss(index1, expected1, this, status, UNDECIDED) || array.get(index1) == this || status.get() == SUCCESS) {
                if (dcss(index2, expected2, this, status, UNDECIDED) || array.get(index2) == this || status.get() == SUCCESS) {
                    return (status.get() == SUCCESS || updateStatus(SUCCESS)).also {
                        if (it)
                            updateCells()
                        else {
                            dcss(index1, this, expected1, status, FAILED)
                            dcss(index2, this, expected2, status, FAILED)
                        }
                    }
                } else {
                    updateStatus(FAILED)
                    array.compareAndSet(index1, this, expected1)
                    return false
                }
            } else {
                updateStatus(FAILED)
                return false
            }

//            if (status.get() != SUCCESS && !dcss(index1, expected1, this, status, UNDECIDED))
//                if (array.get(index1) != this) {
//                    updateStatus(FAILED)
//                    return false
//                }
//            if (status.get() != SUCCESS && !dcss(index2, expected2, this, status, UNDECIDED))
//                if (array.get(index2) != this) {
//                    updateStatus(FAILED)
//                    array.compareAndSet(index1, this, expected1)
//                    return false
//                }
//
//            if (status.get() == SUCCESS || updateStatus(SUCCESS)) {
//                updateCells()
//                return true
//            } else {
//                dcss(index1, this, expected1, status, FAILED)
//                dcss(index2, this, expected2, status, FAILED)
//                return false
//            }
        }

        private fun installDescriptor(): Boolean {
            while (true) {
                if (dcss(index1, expected1, this, status, UNDECIDED) || array.get(index1) == this) {
                    if (dcss(index2, expected2, this, status, UNDECIDED) || array.get(index2) == this) {
                        val result = updateStatus(SUCCESS)
                        if (!result && status.get() == FAILED)
                            dcss(index2, this, expected2, status, FAILED)
                        return result
                    } else {
                        val v = array.get(index2)
                        if (v is AtomicArrayWithCAS2AndImplementedDCSS<*>.CAS2Descriptor) {
                            //todo(vg): need to help with B!!!
                            (v as AtomicArrayWithCAS2AndImplementedDCSS<E>.CAS2Descriptor).helpToProcess()
                        } else {
                            //note(vg): we have failed to install the descriptor, need to roll back the change in the cell #1
                            updateStatus(FAILED) //note(vg): should we handle specifically the case when another thread helped us???
                            dcss(index1, this, expected1, status, FAILED)
                            //if (result || status.get() != SUCCESS)
                            //    array.compareAndSet(index1, this, expected1) //note(vg): should we handle specifically the case when another thread helped us???
                            return false
                        }
                    }
                } else {
                    val v = array.get(index1)
                    if (v is AtomicArrayWithCAS2AndImplementedDCSS<*>.CAS2Descriptor) {
                        //todo(vg): need to help with A!!!
                        (v as AtomicArrayWithCAS2AndImplementedDCSS<E>.CAS2Descriptor).helpToProcess()
                    } else if (v == expected1 && status.get() == UNDECIDED) {
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
            dcss(index1, this, update1, status, SUCCESS)
            dcss(index2, this, update2, status, SUCCESS)
        }

        fun apply() {
            // TODO: Copy the implementation from `AtomicArrayWithCAS2Simplified`
            // TODO: and use `dcss(..)` to install the descriptor.
            if (installDescriptor())
                updateCells()
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }

    // TODO: Please use this DCSS implementation to ensure that
    // TODO: the status is `UNDECIDED` when installing the descriptor.
    fun dcss(
        index: Int,
        expectedCellState: Any?,
        updateCellState: Any?,
        statusReference: AtomicReference<*>,
        expectedStatus: Any?
    ): Boolean =
        if (array[index] == expectedCellState && statusReference.get() == expectedStatus) {
            array[index] = updateCellState
            true
        } else {
            false
        }
}