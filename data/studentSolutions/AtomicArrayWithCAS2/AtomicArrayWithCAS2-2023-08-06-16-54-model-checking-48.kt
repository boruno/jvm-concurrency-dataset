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

package day3

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
                cell.apply()
                get(index)
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
                        // if (array[index].compareAndSet(expected, this)) { return }
                        if (dcss(index, expected, this)) { return }
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

    fun dcss(index: Int, expected: E?, update: Any?): Boolean =
        DCSSDescriptor(index = index, expected = expected, update = update).also { it.apply() }.doesSucceeded()

    inner class DCSSDescriptor(
        public val index: Int,
        public val expected: E?,
        public val update: Any?
    ) {
        private val status = atomic(Status.UNDECIDED)

        fun doesSucceeded () : Boolean = status.value == Status.SUCCESS

        fun tryToHelp (descr : AtomicArrayWithCAS2<*>.DCSSDescriptor = this) = when (descr.status.value) {
            Status.UNDECIDED -> descr.apply()
            Status.FAILED    -> array[descr.index].compareAndSet(descr, descr.expected).let { Unit }
            Status.SUCCESS   -> array[descr.index].compareAndSet(descr, descr.update  ).let { Unit }
        }

        private fun installDescriptor () : Boolean {
            while (true) {
                when (val cell = array[index].value) {
                    this     -> return true
                    is AtomicArrayWithCAS2<*>.DCSSDescriptor -> tryToHelp(cell)
                    expected -> if (array[index].compareAndSet(cell, this)) { return true }
                    else     -> return false
                }
            }
        }

        private fun applyPhysically () : Unit = when (status.value) {
            Status.SUCCESS -> array[index].compareAndSet(this, update  ).let { Unit }
            Status.FAILED  -> array[index].compareAndSet(this, expected).let { Unit }
            else           -> assert(false)
        }

        private fun applyLogically () {
            while (true) {
                when (status.value) {
                    Status.UNDECIDED -> status.compareAndSet(Status.UNDECIDED, Status.SUCCESS)
                    Status.SUCCESS, Status.FAILED -> return
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