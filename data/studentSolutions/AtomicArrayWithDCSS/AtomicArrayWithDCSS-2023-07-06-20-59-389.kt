package day3

import kotlinx.atomicfu.*
import java.lang.IllegalStateException

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
        return when (val e = array[index].value) {
            is AtomicArrayWithDCSS<*>.DCSS -> {
                if (e.status.value == Status.SUCCESS) e.update1 as E
                else e.expected1 as E
            }
            else -> e as E
        }
    }

    fun cas(index: Int, expected: E?, update: E?): Boolean {
        // TODO: the cell can store a descriptor
        return when (val e = array[index].value) {
            expected ->
                if (array[index].compareAndSet(expected, update)) true
                else cas(index, expected, update)
            is AtomicArrayWithDCSS<*>.DCSS -> {
                e.apply()
                cas(index, expected, update)
            }
            else -> false
        }
    }

    fun dcss(
        index1: Int, expected1: E?, update1: E?,
        index2: Int, expected2: E?
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
        // TODO This implementation is not linearizable!
        // TODO Store a DCSS descriptor in array[index1].
        val dcss = DCSS(index1, expected1, index2, expected2, update1)
        dcss.apply()
        return dcss.status.value == Status.SUCCESS
    }

    inner class DCSS(
        val index1: Int,
        val expected1: E?,
        val index2: Int,
        val expected2: E?,
        val update1: E?) {

        val status = atomic(Status.UNDECIDED)

        fun apply() {
            if (installDescriptor() && get(index2) == expected2) {
                status.compareAndSet(Status.UNDECIDED, Status.SUCCESS)
            } else {
                status.compareAndSet(Status.UNDECIDED, Status.FAILED)
            }
            updateValue()
        }

        private fun installDescriptor(): Boolean {
            while (true) {
                when (val e = array[index1].value) {
                    this -> return true
                    is AtomicArrayWithDCSS<*>.DCSS -> e.apply()
                    expected1 -> if (array[index1].compareAndSet(expected1, this)) return true
                    else -> return false
                }
            }
        }

        private fun updateValue() {
            when (status.value) {
                Status.SUCCESS -> array[index1].compareAndSet(this, update1)
                Status.FAILED -> array[index1].compareAndSet(this, expected1)
                else -> throw IllegalStateException()
            }
        }
    }
    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}