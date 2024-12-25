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
        return array[index].value as E?
    }

    fun cas(index: Int, expected: E?, update: E?): Boolean {
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
        val dcssDescriptor = DCSSDescriptor(index1, expected1, update1, index2, expected2)
        dcssDescriptor.apply()
        return dcssDescriptor.status.value == Status.SUCCESS
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
                val result = tryToSetValue(index1, expected1) && tryToSetValue(index2, expected2)
                if (result) {
                    status.compareAndSet(
                        Status.UNDECIDED,
                        Status.SUCCESS
                    )
                } else {
                    status.compareAndSet(
                        Status.UNDECIDED,
                        Status.FAILED
                    )
                }
            }
            updateValues()
        }

        private fun tryToSetValue(index: Int, expected: E): Boolean {
            while (!array[index].compareAndSet(expected, this)) {
                val currentState: Any? = array[index].value
                if (currentState is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
                    if (currentState === this) return true
                    currentState.apply()
                    // The problem was here
                } else if (currentState !== expected) {
                    return false
                }
            }
            return true
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