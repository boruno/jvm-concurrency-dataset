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
            val cell = array[index].value
            if (cell is AtomicArrayWithDCSS<*>.DCSSDescriptor) {
                cell.tryToHelp(cell)
                continue
            } else {
                if (array[index].compareAndSet(expected, update)) { return true }
                if (cell != expected) { return false }
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
        val descriptor = DCSSDescriptor(
                    index1 = index1, expected1 = expected1, update1 = update1,
                    index2 = index2, expected2 = expected2)
        descriptor.apply()
        return descriptor.status.value === Status.SUCCESS
    }

    inner class DCSSDescriptor(
        public val index1: Int,
        public val expected1: E?,
        public val update1: E?,
        public val index2: Int,
        public val expected2: E?
    ) {
        val status = atomic(Status.UNDECIDED)

        fun tryToHelp (descr : AtomicArrayWithDCSS<*>.DCSSDescriptor) = when (descr.status.value) {
            Status.UNDECIDED -> descr.apply()
            Status.FAILED    -> array[descr.index1].compareAndSet(descr, descr.expected1).let { Unit }
            Status.SUCCESS   -> array[descr.index1].compareAndSet(descr, descr.update1).let { Unit }
        }

        private fun installDescriptor () {
            while (true) {
                val cell = array[index1].value
                if (cell == this) { return }
                when (cell) {
                    is AtomicArrayWithDCSS<*>.DCSSDescriptor -> {
                        tryToHelp(cell)
                        continue
                    }
                    else -> {
                        if (cell != expected1) { return }
                        if (array[index1].compareAndSet(cell, this)) { return }
                    }
                }
            }
        }

        fun apply () {
            installDescriptor()


            while (true) {
                if (status.value == Status.SUCCESS || status.value == Status.FAILED) { break }
                val cell2 = array[index2].value
                if (cell2 == expected2) {
                    if (status.compareAndSet(Status.UNDECIDED, Status.SUCCESS)) {
                        break
                    }
                } else {
                    if (cell2 !is AtomicArrayWithDCSS<*>.DCSSDescriptor) {
                        if (status.compareAndSet(Status.UNDECIDED, Status.FAILED) || status.value == Status.FAILED) {
                            break
                        }
                    } else {
                        if (index1 < cell2.index1) {
                            tryToHelp(cell2)
                        } else {
                            if (cell2.status.value == Status.SUCCESS) {
                                array[cell2.index1].compareAndSet(cell2, cell2.update1)
                            } else if (cell2.status.value == Status.FAILED) {
                                array[cell2.index1].compareAndSet(cell2, cell2.expected1)
                            } else {
                                if (cell2.expected1 == expected2) {
                                    status.compareAndSet(Status.UNDECIDED, Status.SUCCESS)
                                } else {
                                    status.compareAndSet(Status.UNDECIDED, Status.FAILED)
                                }
                            }
                        }
                    }
                }
            }

            when (status.value) {
                Status.SUCCESS -> array[index1].compareAndSet(this, update1)
                Status.FAILED  -> array[index1].compareAndSet(this, expected1)
                else           -> assert(false)
            }
        }
    }

    enum class Status {
            UNDECIDED, FAILED, SUCCESS
    }
}