@file:Suppress("DuplicatedCode", "UNCHECKED_CAST")

//package day3

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

    @Suppress("UNREACHABLE_CODE")
    fun get(index: Int): E? {
        // TODO: Copy the implementation from `AtomicArrayWithCAS2Simplified`
        // TODO: the cell can store CAS2Descriptor
        val v = array.get(index)
        if (v is AtomicArrayWithCAS2<*>.CAS2Descriptor)
            return when (v.status.get()!!) {
                Status.UNDECIDED, Status.FAILED ->
                    when (index) {
                        v.index1 -> return v.expected1 as E
                        v.index2 -> return v.expected2 as E
                        else -> throw IllegalArgumentException()
                    }
                Status.SUCCESS ->
                    when (index) {
                        v.index1 -> return v.update1 as E
                        v.index2 -> return v.update2 as E
                        else -> throw IllegalArgumentException()
                    }
            }
        return v as E
    }

    fun cas2(
        index1: Int, expected1: E?, update1: E?,
        index2: Int, expected2: E?, update2: E?
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
        // TODO: Store a CAS2 descriptor in array[index1].
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
        return descriptor.status.get() === Status.SUCCESS
    }

    inner class CAS2Descriptor(
        val index1: Int,
        val expected1: E?,
        val update1: E?,
        val index2: Int,
        val expected2: E?,
        val update2: E?
    ) {
        val status: AtomicReference<Status> = AtomicReference(Status.UNDECIDED)

        private fun helpToProcess(): Boolean {
            while (true) {
                if (dcss(index1, expected1, this, status, Status.UNDECIDED) || array.get(index1) == this || status.get() == Status.SUCCESS) {
                    if (dcss(index2, expected2, this, status, Status.UNDECIDED) || array.get(index2) == this || status.get() == Status.SUCCESS) {
                        return (status.get() == Status.SUCCESS || updateStatus(Status.SUCCESS)).also {
                            if (it)
                                updateCells()
                            else {
                                dcss(index1, this, expected1, status, Status.FAILED)
                                dcss(index2, this, expected2, status, Status.FAILED)
                            }
                        }
                    } else {
                        val v = array.get(index2)
                        if (v is AtomicArrayWithCAS2<*>.CAS2Descriptor) {
                            //todo(vg): need to help with B!!!
                            (v as AtomicArrayWithCAS2<E>.CAS2Descriptor).helpToProcess()
                        } else {
                            //note(vg): we have failed to install the descriptor, but it's possible that it occurs
                            // because dcss failed because of (another) descriptor, then context was switched, that
                            // descriptor was processed and replaced with a value and now we are here.
                            // Question: how to detect such a situation??? In such case we should try again, but in
                            // regular case we should fail.

                            if (v == expected2 && status.get() == Status.UNDECIDED)
                                continue;

                            updateStatus(Status.FAILED)
                            dcss(index1, this, expected1, status, Status.FAILED)
                            return false
                        }
                    }
                } else {
                    val v = array.get(index1)
                    if (v is AtomicArrayWithCAS2<*>.CAS2Descriptor) {
                        //todo(vg): need to help with A!!!
                        (v as AtomicArrayWithCAS2<E>.CAS2Descriptor).helpToProcess()
                    } else if (v == expected1 && status.get() == Status.UNDECIDED) {
                        continue
                    } else {
                        return false
                    }
                }
            }
        }

        private fun installDescriptor(): Boolean {
            while (true) {
                if (dcss(index1, expected1, this, status, Status.UNDECIDED) || array.get(index1) == this) {
                    if (dcss(index2, expected2, this, status, Status.UNDECIDED) || array.get(index2) == this) {
                        val result = updateStatus(Status.SUCCESS)
                        if (!result && status.get() == Status.FAILED)
                            dcss(index2, this, expected2, status, Status.FAILED)
                        return result
                    } else {
                        val v = array.get(index2)
                        if (v is AtomicArrayWithCAS2<*>.CAS2Descriptor) {
                            //todo(vg): need to help with B!!!
                            (v as AtomicArrayWithCAS2<E>.CAS2Descriptor).helpToProcess()
                        } else {
                            //note(vg): we have failed to install the descriptor, but it's possible that it occurs
                            // because dcss failed because of (another) descriptor, then context was switched, that
                            // descriptor was processed and replaced with a value and now we are here.
                            // Question: how to detect such a situation??? In such case we should try again, but in
                            // regular case we should fail.

                            if (v == expected2 && status.get() == Status.UNDECIDED)
                                continue;

                            updateStatus(Status.FAILED) //note(vg): should we handle specifically the case when another thread helped us???
                            dcss(index1, this, expected1, status, Status.FAILED)
                            return false
                        }
                    }
                } else {
                    val v = array.get(index1)
                    if (v is AtomicArrayWithCAS2<*>.CAS2Descriptor) {
                        //todo(vg): need to help with A!!!
                        (v as AtomicArrayWithCAS2<E>.CAS2Descriptor).helpToProcess()
                    } else if (v == expected1 && status.get() == Status.UNDECIDED) {
                        continue
                    } else {
                        return false
                    }
                }
            }
        }

        private fun updateStatus(newStatus: Status): Boolean {
            return status.compareAndSet(Status.UNDECIDED, newStatus)
        }

        private fun updateCells() {
            dcss(index1, this, update1, status, Status.SUCCESS)
            dcss(index2, this, update2, status, Status.SUCCESS)
        }

        fun apply() {
            // TODO: Copy the implementation from `AtomicArrayWithCAS2Simplified`
            // TODO: and use `dcss(..)` to install the descriptor.
            if (installDescriptor())
                updateCells()
        }
    }

    inner class DCSSDescriptor(
        val index: Int,
        val expected: Any?,
        val update: Any?,
        val statusReference: AtomicReference<Status>,
        val expectedStatus: Status
    ) {
        val status: AtomicReference<Status> = AtomicReference(Status.UNDECIDED)

//        if (array[index] == expectedCellState && statusReference.get() == expectedStatus) {
//            array[index] = updateCellState
//            true
//        } else {
//            false
//        }
        fun apply() {
            if (array.compareAndSet(index, expected, this)) {
                if (statusReference.get() == expectedStatus) {
                    if (status.compareAndSet(Status.UNDECIDED, Status.SUCCESS)) {
                        array.compareAndSet(index, this, update)
                        return
                    }
                }
            }
            status.compareAndSet(Status.UNDECIDED, Status.FAILED)
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
        statusReference: AtomicReference<Status>,
        expectedStatus: Status
    ): Boolean {
        val descriptor = DCSSDescriptor(index, expectedCellState, updateCellState, statusReference, expectedStatus)
        descriptor.apply()
        return descriptor.status.get() === Status.SUCCESS
    }
}