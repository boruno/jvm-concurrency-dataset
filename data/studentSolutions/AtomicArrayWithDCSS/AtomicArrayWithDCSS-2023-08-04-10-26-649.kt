//package day3

import AtomicArrayWithDCSS.Status.*
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
        while (true) {
            val state = array[index].value
            when {
                state is AtomicArrayWithDCSS<*>.DCSS -> {
                    state.complete()
                }
                else -> {
                    return state as E?
                }
            }
        }
    }

    fun cas(index: Int, expected: E?, update: E?): Boolean {
        while (true) {
            val curValue = get(index)
            if (curValue !== expected) {
                return false
            }
            if (array[index].compareAndSet(curValue, update)) {
                return true
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
        val dcss = DCSS(index1, expected1, update1, index2, expected2)
        dcss.install()
        dcss.complete()
        return dcss.status.value == SUCCESS
    }

    inner class DCSS(
        val index1: Int,
        val expected1: Any?,
        val update1: Any?,
        val index2: Int,
        val expected2: Any?
    ) {
        val status = atomic(UNDECIDED)

        fun install() {
            while (true) {
                val curValue = get(index1)
                when {
                    curValue !== expected1 -> {
                        status.value = FAILURE
                        return
                    }
                    array[index1].compareAndSet(curValue, this) -> {
                        return
                    }
                }
            }
        }

        fun complete() {
            val value2 = get(index2)
            if (value2 === expected2) {
                status.compareAndSet(UNDECIDED, SUCCESS)
            } else {
                status.compareAndSet(UNDECIDED, FAILURE)
            }

            if (status.value == SUCCESS) {
                array[index1].compareAndSet(this, update1)
            } else {
                array[index1].compareAndSet(this, expected1)
            }
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILURE
    }
}