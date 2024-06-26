package day3

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
        val value = array[index].value
        return when {
            value is AtomicArrayWithDCSS<*>.DCSSDescriptor -> when (value.status.value) {
                Status.SUCCESS -> value.update1 as E?
                else -> value.expected1 as E?
            }
            else -> value as E?
        }
    }

    fun cas(index: Int, expected: E?, update: E?): Boolean {
        // TODO: the cell can store a descriptor
        while(!array[index].compareAndSet(expected, update)) {
            val curState = array[index].value
            if(curState is AtomicArrayWithDCSS<*>.DCSSDescriptor) {
                curState.apply()
                continue
            }
            if (curState != expected) {
                return false;
            }
        }
        return true
    }

    fun dcss(
        index1: Int, expected1: E?, update1: E?,
        index2: Int, expected2: E?
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
        // TODO This implementation is not linearizable!
        // TODO Store a DCSS descriptor in array[index1].
        val dcssDescriptor = DCSSDescriptor(index1, expected1, update1, index2, expected2)
        dcssDescriptor.apply()

        return true
    }

    inner class DCSSDescriptor(val index1: Int,
                               val expected1: E?,
                               val update1: E?,
                               val index2: Int,
                               val expected2: E?) {
        val status = atomic(Status.UNDECIDED)

        fun apply() {
            while (!array[index1].compareAndSet(expected1, this)) {
                val curState = array[index1].value
                if (curState is AtomicArrayWithDCSS<*>.DCSSDescriptor) {
                    if (curState == this) {
                        break
                    }
                    if (curState.status.value == Status.UNDECIDED) {
                        curState.apply()
                    } else {
                        curState.applyValues()
                    }
                } else if (curState != expected1) {
                    status.compareAndSet(Status.UNDECIDED, Status.FAILED)
                    break
                }
            }

            if (array[index2].value != expected2) {
                status.compareAndSet(Status.UNDECIDED, Status.FAILED)
            }
            status.compareAndSet(Status.UNDECIDED, Status.SUCCESS)
            applyValues()
        }

        private fun applyValues() {
            val status = status.value
            if (status == Status.SUCCESS) {
                array[index1].compareAndSet(this, update1)
            } else {
                array[index1].compareAndSet(this, expected1)
            }
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}