//package day3

import day3.AtomicArrayWithDCSS.Status.*
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
//        return array[index].value as E?
        val curValue = array[index].value
        return (if (curValue is AtomicArrayWithDCSS<*>.DCSSDescriptor) curValue.value() else curValue) as E?
    }

    fun cas(index: Int, expected: E?, update: E?): Boolean {
        // TODO: the cell can store a descriptor
        val curValue = array[index].value
        while (true) {
            when (curValue) {
                is AtomicArrayWithDCSS<*>.DCSSDescriptor -> {
                    curValue.updateStatus()
                    curValue.updateCell()
                }
                else -> return array[index].compareAndSet(expected, update)
            }
        }

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
        val descriptor = DCSSDescriptor(index = index1, expected = expected1, update = update1)
        descriptor.install()
        descriptor.updateStatus()
        return if (descriptor.status.value == SUCCESS && array[index2].value == expected2) {
            descriptor.updateCell()
            true
        } else {
            descriptor.status.value = FAILED
            descriptor.updateCell()
            false
        }
    }

    inner class DCSSDescriptor(
        private val index: Int,
        private val expected: E?,
        private val update: E?
    ) {
        val status = atomic(UNDECIDED)

        fun value(): E? = if (status.value == SUCCESS) update else expected

        fun apply() {
            install()
            updateStatus()
            updateCell()
        }

        fun install() {
            array[index].compareAndSet(expected, this)
        }

        fun updateStatus() {
            val newStatus = if (array[index].value == this) SUCCESS else FAILED
            status.compareAndSet(UNDECIDED, newStatus)
        }

        fun updateCell() {
            val newValue = if (status.value == SUCCESS) update else expected
            array[index].compareAndSet(this, newValue)
        }

    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}