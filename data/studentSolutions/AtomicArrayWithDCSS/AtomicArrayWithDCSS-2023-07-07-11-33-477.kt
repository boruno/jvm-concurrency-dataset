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

    fun get(index: Int): E {
        val value = array[index].value
        if (value is AtomicArrayWithDCSS<*>.DCSSDescriptor) {
            if (value.index1 == index && value.status.value != Status.SUCCESS) return value.expected1 as E
            if (value.index1 == index && value.status.value == Status.SUCCESS) return value.update1 as E
        }
        return value as E
    }

    fun cas(index: Int, expected: E?, update: E?): Boolean {
        // TODO: the cell can store a descriptor
        while (true) {
            if (array[index].compareAndSet(expected, update)) return true
            val value = array[index].value
            when {
                value is AtomicArrayWithDCSS<*>.DCSSDescriptor -> {
                    value.justHelp()
                }
                value === expected -> {
                    continue
                }
                else -> {
                    return false
                }
            }
        }
    }

    fun dcss(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
        // TODO This implementation is not linearizable!
        // TODO Store a DCSS descriptor in array[index1].
        val dcssDescriptor = DCSSDescriptor(index1, expected1, update1, index2, expected2)
        dcssDescriptor.apply()
        return dcssDescriptor.status.value === Status.SUCCESS
    }

    inner class DCSSDescriptor(
        val index1: Int,
        val expected1: E,
        val update1: E,
        val index2: Int,
        val expected2: E
    ) {
        val status = atomic(Status.UNDECIDED)

        fun apply() {
            if (status.value === Status.UNDECIDED) {
                if (!installDescriptor()) {
                    status.compareAndSet(Status.UNDECIDED, Status.FAILED)
                } else if (status.value === Status.UNDECIDED) {
                    tryToUpdateValue()
                }
            }
            updateValues()
        }

        fun justHelp() {
            if (status.value === Status.UNDECIDED) {
                tryToUpdateValue()
            }
            updateValues()
        }

        private fun installDescriptor(): Boolean {
            while (true) {
                val currentState: Any? = array[index1].value
                if (array[index1].compareAndSet(expected1, this)) return true
                if (currentState is AtomicArrayWithDCSS<*>.DCSSDescriptor) {
                    if (currentState === this) return true
                    currentState.justHelp()
                    // The problem was here
                } else if (currentState !== expected1) {
                    return false
                }
            }
        }

         private fun tryToUpdateValue() {
             while (true) {
                 val currentState = array[index2].value
                 if (currentState === expected2) {
                     status.compareAndSet(Status.UNDECIDED, Status.SUCCESS)
                     return
                 }
/*                 if (currentState is AtomicArrayWithDCSS<*>.DCSSDescriptor) {
                     if (currentState === this) return
                     currentState.justHelp()
                 } */
                 else {
                     status.compareAndSet(Status.UNDECIDED, Status.FAILED)
                     return
                 }
             }
         }

        private fun updateValues() {
            if (status.value == Status.SUCCESS) {
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