//@file:Suppress("DuplicatedCode")
//
//package day3
//
//import kotlinx.atomicfu.*
//
//// This implementation never stores `null` values.
//class AtomicArrayWithCAS2<E : Any>(size: Int, initialValue: E) {
//    private val array = atomicArrayOfNulls<Any?>(size)
//
//    init {
//        // Fill array with the initial value.
//        for (i in 0 until size) {
//            array[i].value = initialValue
//        }
//    }
//
//    fun get(index: Int): E? {
//        // TODO: the cell can store a descriptor
//        return array[index].value as E?
//    }
//
//    fun cas(index: Int, expected: E?, update: E?): Boolean {
//        // TODO: the cell can store a descriptor
//        return array[index].compareAndSet(expected, update)
//    }
//
//    fun cas2(
//        index1: Int, expected1: E?, update1: E?,
//        index2: Int, expected2: E?, update2: E?
//    ): Boolean {
//        require(index1 != index2) { "The indices should be different" }
//        // TODO: this implementation is not linearizable,
//        // TODO: Store a CAS2 descriptor in array[index1].
//        if (array[index1].value != expected1 || array[index2].value != expected2) return false
//        array[index1].value = update1
//        array[index2].value = update2
//        return true
//    }
//}

@file:Suppress("DuplicatedCode")

//package day3

import kotlinx.atomicfu.*

// This implementation never stores `null` values.
class AtomicArrayWithCAS2<E : Any>(size: Int, initialValue: E) {
    private val array = atomicArrayOfNulls<Any?>(size)

    init {
        // Fill array with the initial value.
        for (i in 0 until size) {
            array[i].value = initialValue
        }
    }

    fun get(index: Int): E? =
//        // TODO: the cell can store a descriptor
//        return array[index].value as E?
        when (val cell = array[index].value) {
            is AtomicArrayWithCAS2<*>.DCSSDescriptor -> {
                cell.apply()
                get(index)
            }
            is AtomicArrayWithCAS2<*>.CAS2Descriptor -> {
                val updated  = if (cell.index1 == index) cell.update1   else cell.update2
                val expected = if (cell.index1 == index) cell.expected1 else cell.expected2
                if (cell.status.value === Status.SUCCESS) { updated } else { expected }
            }
            else -> cell
        } as E?

    fun cas(index: Int, expected: E?, update: E?): Boolean {
        while (true) {
            when (val cell = array[index].value) {
                is AtomicArrayWithCAS2<*>.DCSSDescriptor -> cell.tryToHelp()
                is AtomicArrayWithCAS2<*>.CAS2Descriptor -> cell.apply()
                expected -> if (array[index].compareAndSet(expected, update)) { return true }
                else     -> return false
            }
        }
    }

    fun cas2(
        index1: Int, expected1: E?, update1: E?,
        index2: Int, expected2: E?, update2: E?
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
        val descriptor =
            if (index1 < index2) {
                CAS2Descriptor(
                    index1 = index1, expected1 = expected1, update1 = update1,
                    index2 = index2, expected2 = expected2, update2 = update2)
            } else {
                CAS2Descriptor(
                    index1 = index2, expected1 = expected2, update1 = update2,
                    index2 = index1, expected2 = expected1, update2 = update1)
            }
        descriptor.apply()
        return descriptor.status.value === Status.SUCCESS
    }

    inner class CAS2Descriptor(
        public val index1: Int,
        public val expected1: E?,
        public val update1: E?,
        public val index2: Int,
        public val expected2: E?,
        public val update2: E?
    ) {
        val status = atomic(Status.UNDECIDED)

        private fun restoreValues(obj: AtomicArrayWithCAS2<*>.CAS2Descriptor) {
            array[obj.index1].compareAndSet(obj, obj.expected1)
            array[obj.index2].compareAndSet(obj, obj.expected2)
        }

        private fun updateValues(obj: AtomicArrayWithCAS2<*>.CAS2Descriptor) {
            array[obj.index1].compareAndSet(obj, obj.update1)
            array[obj.index2].compareAndSet(obj, obj.update2)
        }

        private fun fail() {
            if (status.compareAndSet(Status.UNDECIDED, Status.FAILED)) {
                restoreValues(this)
            }
        }

        private fun handleCell (flag : Boolean) {
            val index = if (flag) index1 else index2
            val expected = if (flag) expected1 else expected2
            while (true) {
                if (status.value == Status.SUCCESS || status.value == Status.FAILED) { return } // new
                var old = array[index].value
                if (old == this) { break }
                if (old is AtomicArrayWithCAS2<*>.CAS2Descriptor) {
                    when (old.status.value) {
                        Status.SUCCESS -> updateValues(old)
                        Status.UNDECIDED -> old.apply()
                        Status.FAILED -> restoreValues(old)
                    }
                } else { // CASE: old is a value
                    if (old == expected) {
                         // if (dcss(index, expected, this, status, Status.UNDECIDED)) { return } // DIDN'T GET HOW IT SHOULD WORK since status is not an index and this is not E?
                        if (status.value == Status.SUCCESS || status.value == Status.FAILED) { return } // new
                        if (array[index].compareAndSet(expected, this)) { return }
                    } else {
                        fail()
                        return
                    }
                }
            }
        }

        fun apply() {
            handleCell(true)
            handleCell(false)

            while (true) {
                if (status.compareAndSet(Status.UNDECIDED, Status.SUCCESS) || status.value == Status.SUCCESS) {
                    updateValues(this)
                    return
                }
                if (status.value == Status.FAILED) {
                    restoreValues(this)
                    return
                }
            }
        }


    }

    fun dcss(
        index1: Int, expected1: E?, update1: E?,
        index2: Int, expected2: E?
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
//        // TODO This implementation is not linearizable!
//        // TODO Store a DCSS descriptor in array[index1].
//        if (array[index1].value != expected1 || array[index2].value != expected2) return false
//        array[index1].value = update1
//        return true
        return DCSSDescriptor(index1 = index1, expected1 = expected1, update1 = update1,index2 = index2,
            expected2 = expected2).also { it.apply() }.doesSucceeded()
    }

    inner class DCSSDescriptor(
        public val index1: Int,
        public val expected1: E?,
        public val update1: E?,
        public val index2: Int,
        public val expected2: E?
    ) {
        private val status = atomic(AtomicArrayWithCAS2.Status.UNDECIDED)

        fun doesSucceeded () : Boolean = status.value == AtomicArrayWithCAS2.Status.SUCCESS

        fun tryToHelp (descr : AtomicArrayWithCAS2<*>.DCSSDescriptor = this) = when (descr.status.value) {
            AtomicArrayWithCAS2.Status.UNDECIDED -> descr.apply()
            AtomicArrayWithCAS2.Status.FAILED    -> array[descr.index1].compareAndSet(descr, descr.expected1).let { Unit }
            AtomicArrayWithCAS2.Status.SUCCESS   -> array[descr.index1].compareAndSet(descr, descr.update1  ).let { Unit }
        }

        // returns true if descriptor is installed; false if DCSS is failing and no descriptor is installed
        private fun installDescriptor () : Boolean {
            while (true) {
                when (val cell = array[index1].value) {
                    this -> return true
                    is AtomicArrayWithCAS2<*>.DCSSDescriptor -> tryToHelp(cell)
                    expected1 -> if (array[index1].compareAndSet(cell, this)) { return true }
                    else -> return false
                }
            }
        }

        private fun applyPhysically () : Unit = when (status.value) {
            AtomicArrayWithCAS2.Status.SUCCESS -> array[index1].compareAndSet(this, update1  ).let { Unit }
            AtomicArrayWithCAS2.Status.FAILED  -> array[index1].compareAndSet(this, expected1).let { Unit }
            else           -> assert(false)
        }

        private fun applyLogically () {
            while (true) {
                if (status.value == AtomicArrayWithCAS2.Status.SUCCESS || status.value == AtomicArrayWithCAS2.Status.FAILED) { return }
                when (val cell2 = array[index2].value) {
                    expected2 -> status.compareAndSet(AtomicArrayWithCAS2.Status.UNDECIDED, AtomicArrayWithCAS2.Status.SUCCESS)
                    !is AtomicArrayWithCAS2<*>.DCSSDescriptor -> {
                        // WEIRD: next line works but replacing next line with the scrutinee fails on modelChecking tests only
                        if (status.compareAndSet(AtomicArrayWithCAS2.Status.UNDECIDED, AtomicArrayWithCAS2.Status.FAILED)) { return }
                    }
                    else -> if (index1 < cell2.index1) { tryToHelp(cell2) }
                    else {
                        val newStatus = if (cell2.expected1 == expected2) AtomicArrayWithCAS2.Status.SUCCESS else AtomicArrayWithCAS2.Status.FAILED
                        status.compareAndSet(AtomicArrayWithCAS2.Status.UNDECIDED, newStatus)
                    }
                }
            }
        }

        fun apply () {
            if (!installDescriptor()) { return }
            applyLogically ()
            applyPhysically()
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}