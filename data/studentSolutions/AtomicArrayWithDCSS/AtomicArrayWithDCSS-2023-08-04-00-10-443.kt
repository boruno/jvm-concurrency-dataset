package day3

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
        while (true) {
            when (val curValue = array[index].value) {
                is AtomicArrayWithDCSS<*>.DCSSDescriptor -> {
                    curValue.updateStatus()
                    curValue.updateCell()
                }
                expected -> if (array[index].compareAndSet(expected, update)) return true else continue
                else -> return false
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
        val descriptor = DCSSDescriptor(
            index1 = index1, expected1 = expected1, update = update1,
            index2 = index2, expected2 = expected2
        )
        descriptor.apply()
        return descriptor.status.value == SUCCESS
    }

    inner class DCSSDescriptor(
        private val index1: Int,
        private val expected1: E?,
        private val update: E?,
        private val index2: Int,
        private val expected2: E?,
    ) {
        val status = atomic(UNDECIDED)

        fun value(): E? = if (status.value == SUCCESS) update else expected1

        fun apply() {
            install()
            updateStatus()
            updateCell()
        }

        fun install() {
            if (status.value != UNDECIDED) return

            while (true) {
                when (val curValue = array[index1].value) {
                    this -> return
                    is AtomicArrayWithDCSS<*>.DCSSDescriptor -> {
                        curValue.updateStatus()
                        curValue.updateCell()
                    }
                    expected1 -> if (array[index1].compareAndSet(expected1, this)) return else continue
                    else -> return
                }
            }
        }

        fun updateStatus() {
            val newStatus = getNewStatus()
            status.compareAndSet(UNDECIDED, newStatus)
        }

        fun getNewStatus(): Status {
            while (true) {
                val val1 = array[index1].value
                var val2 = array[index2].value
                when {
                    val1 != this -> return FAILED
                    val2 is AtomicArrayWithDCSS<*>.DCSSDescriptor -> {
                        if (val2.index1 < this.index1) {
                            val2.updateStatus()
                            val2.updateCell()
                        } else {
                            if (val2.value() == expected2) return SUCCESS
                        }
                    }
                    val2 == expected2 -> return SUCCESS
                    else -> return FAILED
                }
            }
        }

        fun updateCell() {
            val newValue = if (status.value == SUCCESS) update else expected1
            array[index1].compareAndSet(this, newValue)
        }

    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}