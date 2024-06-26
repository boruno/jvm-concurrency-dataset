package day3

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
                cell.expected1
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

        public fun tryToHelp (descr : AtomicArrayWithDCSS<*>.DCSSDescriptor) {
            while (true) {
                when (descr.status.value) {
                    Status.UNDECIDED -> {
                        descr.apply()
                        return
                    }

                    Status.FAILED -> {
                        array[descr.index1].compareAndSet(descr, descr.expected1)
                        return
                    }

                    Status.MAYBE -> {
                        if (array[descr.index2].value == descr.expected2) {
                            descr.status.compareAndSet(Status.MAYBE, Status.SUCCESS)
                        } else {
                            descr.status.compareAndSet(Status.MAYBE, Status.UNDECIDED)
                        }
                        continue
                    }

                    Status.SUCCESS -> {
                        array[descr.index1].compareAndSet(descr, update1)
                        return
                    }
                }
            }
        }

        fun apply () {
            var cell = array[index1].value
            while (true) {
                cell = array[index1].value
                if (cell == this) { break }
                if (cell is AtomicArrayWithDCSS<*>.DCSSDescriptor) {
                    tryToHelp(cell)
                    continue
                }
                if (cell != expected1) { return }
                if (array[index1].compareAndSet(cell, this)) { break }
            }


            while (true) {
                if (status.value == Status.SUCCESS || status.value == Status.FAILED) { break }
                val cell2 = array[index2].value
                if (cell2 == expected2) {
                    if (status.compareAndSet(Status.UNDECIDED, Status.MAYBE) || status.value == Status.MAYBE) {
                        if (array[index2].value == cell2) {
                            if (status.compareAndSet(Status.MAYBE, Status.SUCCESS)) {
                                break
                            }
                        } else {
                            status.compareAndSet(Status.MAYBE, Status.UNDECIDED)
                        }
                    }
                } else {
                    if (cell2 !is AtomicArrayWithDCSS<*>.DCSSDescriptor) {
                        if (status.compareAndSet(Status.UNDECIDED, Status.FAILED) || status.value == Status.FAILED) {
                            break
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
            UNDECIDED, MAYBE, FAILED, SUCCESS
    }
}