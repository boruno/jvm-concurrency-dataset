@file:Suppress("DuplicatedCode", "UNCHECKED_CAST")

//package day3

import day3.AtomicArrayWithCAS2AndImplementedDCSS.Status.*
import java.util.concurrent.atomic.*
import kotlin.math.max
import kotlin.math.min

//note(vg): Problematic test:
//| ------------------------------------------------------------------------ |
//|        Thread 1        |        Thread 2        |        Thread 3        |
//| ------------------------------------------------------------------------ |
//| get(1)                 |                        |                        |
//| ------------------------------------------------------------------------ |
//| cas2(2, 0, 2, 1, 0, 2) | get(0)                 | cas2(1, 2, 1, 0, 2, 0) |
//| cas2(2, 0, 2, 2, 1, 2) | cas2(0, 0, 2, 1, 1, 1) | get(2)                 |
//| ------------------------------------------------------------------------ |
//
// So, we have following cas2 operations:
//  1. cas2(2, 0, 2, 1, 0, 2)
//  2. cas2(2, 0, 2, 2, 1, 2)
//  3. cas2(0, 0, 2, 1, 1, 1)
//  4. cas2(1, 2, 1, 0, 2, 0)
//
// By cells:
//  cell 0 affected by operations number 3 and 4
//  cell 1 affected by operations number 1, 3 and 4
//  cell 2 affected by operations number 1, 2

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
            if (status.get() != SUCCESS && !array.compareAndSet(index1, expected1, this))
                if (array.get(index1) != this) {
                    updateStatus(FAILED)
                    return false
                }
            if (status.get() != SUCCESS && !array.compareAndSet(index2, expected2, this))
                if (array.get(index2) != this) {
                    updateStatus(FAILED)
                    array.compareAndSet(index1, this, expected1)
                    return false
                }

            if (status.get() == SUCCESS || updateStatus(
                    SUCCESS
                )) {
                updateCells()
                return true
            } else {
                if (status.get() == FAILED) {
                    array.compareAndSet(index1, this, expected1)
                    array.compareAndSet(index2, this, expected2)
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
                        if (v is AtomicArrayWithCAS2AndImplementedDCSS<*>.CAS2Descriptor) {
                            //todo(vg): need to help with B!!!
                            (v as AtomicArrayWithCAS2AndImplementedDCSS<E>.CAS2Descriptor).helpToProcess()
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
                    if (v is AtomicArrayWithCAS2AndImplementedDCSS<*>.CAS2Descriptor) {
                        //todo(vg): need to help with A!!!
                        (v as AtomicArrayWithCAS2AndImplementedDCSS<E>.CAS2Descriptor).helpToProcess()
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