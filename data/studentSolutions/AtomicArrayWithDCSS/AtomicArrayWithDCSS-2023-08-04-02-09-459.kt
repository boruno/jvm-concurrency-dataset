//package day3

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

    fun get(index: Int): E? {
        // TODO: the cell can store a descriptor
        val cell = array[index].value
        if (cell is AtomicArrayWithDCSS<*>.DCSSDescriptor<*>) {
            if (cell.status.value == Status.SUCCESS)
                return cell.update1 as E?
            else
                return cell.expected1 as E?
        }
        return cell as E?
    }

    fun cas(index: Int, expected: E?, update: E?): Boolean {
        // TODO: the cell can store a descriptor
        val cell = array[index].value
        if (cell is AtomicArrayWithDCSS<*>.DCSSDescriptor<*>) {
//            cell.status.compareAndSet()
            cell.apply(true)
        }
        return array[index].compareAndSet(expected, update)
    }

    fun dcss(
        index1: Int, expected1: E?, update1: E?,
        index2: Int, expected2: E?
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
        // TODO This implementation is not linearizable!
        // TODO Store a DCSS descriptor in array[index1].
//        if (array[index1].value != expected1 || array[index2].value != expected2) return false
//        array[index1].value = update1
        val descriptor = DCSSDescriptor(index1, expected1, update1, index2, expected2)
        descriptor.apply(true)
        return descriptor.status.value == Status.SUCCESS
    }


    inner class DCSSDescriptor<E>(
                            val index1: Int, val expected1: E?, val update1: E?,
                            val index2: Int, val expected2: E?
        ) {
        val status = atomic(Status.UNDECIDED)

        fun apply(help: Boolean) {
            if (help) {
                tryHelp()
            }
            else {
                install()
            }
            updateStatus()
            updateCells()
        }

        private fun tryHelp() {
            val cell = array[index1].value
            if (cell != this && cell is AtomicArrayWithDCSS<*>.DCSSDescriptor<*>) {
                cell.apply(false)
            }
        }

        private fun updateCells() {
            if (status.value == Status.SUCCESS)
                array[index1].compareAndSet(this, update1)
            else
                array[index1].compareAndSet(this, expected1)
        }

        private fun install() {
            if (status.value != Status.UNDECIDED)
                return
//            val cell2Value = array[index2].value
            array[index1].compareAndSet(expected1, this)
        }

        private fun updateStatus() {
            if (array[index1].value == this && array[index2].value == expected2)
                status.compareAndSet(Status.UNDECIDED, Status.SUCCESS)
            else
                status.compareAndSet(Status.UNDECIDED, Status.FAILED)
        }


    }
    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }

}