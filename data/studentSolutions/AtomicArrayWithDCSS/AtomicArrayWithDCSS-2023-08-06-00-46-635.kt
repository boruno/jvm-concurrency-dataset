//package day3

import com.sun.net.httpserver.Authenticator.Success
import kotlinx.atomicfu.*

// This implementation never stores `null` values.
class AtomicArrayWithDCSS<E : Any>(size: Int, initialValue: E) {
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
            is AtomicArrayWithDCSS<*>.DCSSDescriptor -> {
                cell.apply()
                get(index)
            }
            else -> cell
        } as E?

    fun cas(index: Int, expected: E?, update: E?): Boolean {
//        // TODO: the cell can store a descriptor
//        return array[index].compareAndSet(expected, update)
        while (true) {
            when (val cell = array[index].value) {
                is AtomicArrayWithDCSS<*>.DCSSDescriptor -> cell.tryToHelp()
                expected -> if (array[index].compareAndSet(expected, update)) { return true }
                else     -> return false
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
        private val status = atomic(Status.UNDECIDED)

        fun doesSucceeded () : Boolean = status.value == Status.SUCCESS

        fun tryToHelp (descr : AtomicArrayWithDCSS<*>.DCSSDescriptor = this) = when (descr.status.value) {
            Status.UNDECIDED -> descr.apply()
            Status.FAILED    -> array[descr.index1].compareAndSet(descr, descr.expected1).let { Unit }
            Status.SUCCESS   -> array[descr.index1].compareAndSet(descr, descr.update1  ).let { Unit }
        }

        // returns true if descriptor is installed; false if DCSS is failing and no descriptor is installed
        private fun installDescriptor () : Boolean {
            while (true) {
                when (val cell = array[index1].value) {
                    this -> return true
                    is AtomicArrayWithDCSS<*>.DCSSDescriptor -> tryToHelp(cell)
                    expected1 -> if (array[index1].compareAndSet(cell, this)) { return true }
                    else -> return false
                }
            }
        }

        private fun applyPhysically () : Unit = when (status.value) {
            Status.SUCCESS -> array[index1].compareAndSet(this, update1  ).let { Unit }
            Status.FAILED  -> array[index1].compareAndSet(this, expected1).let { Unit }
            else           -> assert(false)
        }

        private fun applyLogically () {
            while (true) {
                if (status.value == Status.SUCCESS || status.value == Status.FAILED) { return }
                when (val cell2 = array[index2].value) {
                    expected2 -> status.compareAndSet(Status.UNDECIDED, Status.SUCCESS)
                    !is AtomicArrayWithDCSS<*>.DCSSDescriptor -> {
                        status.compareAndSet(Status.UNDECIDED, Status.FAILED)
                    }
                    else -> if (index1 < cell2.index1) { tryToHelp(cell2) }
                            else {
                                val newStatus = if (cell2.expected1 == expected2) Status.SUCCESS else Status.FAILED
                                status.compareAndSet(Status.UNDECIDED, newStatus)
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
            UNDECIDED, FAILED, SUCCESS
    }
}