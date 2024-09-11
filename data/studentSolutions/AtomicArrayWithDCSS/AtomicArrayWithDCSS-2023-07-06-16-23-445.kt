package day3

import kotlinx.atomicfu.*

// This implementation never stores `null` values.
class AtomicArrayWithDCSS<E : Any>(size: Int, initialValue: E) {
    private val array = atomicArrayOfNulls<Any>(size)

    init {
        // Fill array with the initial value.
        for (i in 0 until size) {
            array[i].value = initialValue
        }
    }

    fun get(index: Int): E {
        // TODO: the cell can store a descriptor
        while (true) {
            val arrayCell = array[index].value
            if (arrayCell is AtomicArrayWithDCSS<*>.DCSSDescriptor) {
                arrayCell.apply()
            } else {
                return arrayCell as E
            }
        }
    }

    fun cas(index: Int, expected: E, update: E): Boolean {
        // TODO: the cell can store a descriptor
        return array[index].compareAndSet(expected, update)
    }

    fun dcss(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
        // TODO This implementation is not linearizable!
        // TODO Store a DCSS descriptor in array[index1].
//        if (array[index1].value != expected1 || array[index2].value != expected2) return false
//        array[index1].value = update1
        val desc = DCSSDescriptor(index1, expected1, update1, index2, expected2)
        desc.apply()
        return desc.status.value === Status.SUCCESS
//        return true
    }

    inner class DCSSDescriptor(
        private val index1: Int,
        private val expected1: E,
        private val update1: E,
        private val index2: Int,
        private val expected2: E,
    ) {
        val status = atomic(Status.UNDECIDED)

        fun apply() {
            if (status.value === Status.UNDECIDED) {
                val success = tryInstallDescriptor()
                if (array[index2].value == expected2 && success) {
//                if (success) {
                    status.compareAndSet(Status.UNDECIDED, Status.SUCCESS)
                } else {
                    status.compareAndSet(Status.UNDECIDED, Status.FAILED)
                }
            }
            updateValue()
        }

        private fun updateValue() {
            if (status.value === Status.SUCCESS) {
                array[index1].compareAndSet(this, update1)
            } else {
                array[index1].compareAndSet(this, expected1)
            }
        }

        private fun tryInstallDescriptor(): Boolean {
            val index = index1

            while (true) {
                val curState = array[index].value

                when {
                    curState === this -> {
                        return true
                    }
                    curState is AtomicArrayWithDCSS<*>.DCSSDescriptor -> {
                        curState.apply()
                    }
                    curState === expected1 -> {
                        if (array[index].compareAndSet(expected1, this)) {
                            return true
                        } else {
                            continue
                        }
                    }
                    else -> {
                        return false
                    }
                }
            }
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}