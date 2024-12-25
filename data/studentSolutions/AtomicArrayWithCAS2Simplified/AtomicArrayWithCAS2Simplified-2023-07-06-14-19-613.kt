//package day3

import day3.AtomicArrayWithCAS2Simplified.Status.*
import kotlinx.atomicfu.*


// This implementation never stores `null` values.
class AtomicArrayWithCAS2Simplified<E : Any>(size: Int, initialValue: E) {
    private val array = atomicArrayOfNulls<Any?>(size)

    init {
        // Fill array with the initial value.
        for (i in 0 until size) {
            array[i].value = initialValue
        }
    }

    fun get(index: Int): E {
        while (true) {
            val state = array[index].value
            if (state is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
                state.apply()
            } else {
                return state as E
            }
        }
//        val state = array[index].value
//        if (state is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
//            return state.get(index) as E
//        } else {
//            return state as E
//        }
    }

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
        val descriptor = if (index1 > index2) {
            CAS2Descriptor(index1, expected1, update1, index2, expected2, update2)
        } else {
            CAS2Descriptor(index2, expected2, update2, index1, expected1, update1)
        }
        descriptor.apply()
        return descriptor.status.value == SUCCESS
    }

    inner class CAS2Descriptor(
        private val index1: Int,
        private val expected1: E,
        private val update1: E,
        private val index2: Int,
        private val expected2: E,
        private val update2: E
    ) {
        val status = atomic(UNDECIDED)

        fun apply() {
            if (status.value === UNDECIDED) {
                val success = tryInstallDescriptor()
                if (success) {
                    status.compareAndSet(UNDECIDED, SUCCESS)
                } else {
                    status.compareAndSet(UNDECIDED, FAILED)
                }
            }
            updateValues()
        }

        private fun tryInstallDescriptor(): Boolean {
            if (!tryInstallDescriptor(index1, expected1)) return false
            if (!tryInstallDescriptor(index2, expected2)) return false
            return true
        }

        private fun tryInstallDescriptor(index: Int, expected: E): Boolean {
            while (true) {
                val currState = array[index].value
                when {
                    currState === this -> {
                        return true //already installed
                    }

                    currState is AtomicArrayWithCAS2SingleWriter<*>.CAS2Descriptor -> {
                        //help and continue
                        currState.apply()
                    }

                    currState === expected -> {
                        if (array[index].compareAndSet(expected, this)) {
                            //successfully installed
                            return true
                        }
                        //or continue
                    }

                    else -> { //value not expected
                        return false
                    }
                }
            }
        }

        private fun updateValues() {
            if (status.value == SUCCESS) {
                array[index1].compareAndSet(this, update1)
                array[index2].compareAndSet(this, update2)
            } else {
                array[index1].compareAndSet(this, expected1)
                array[index2].compareAndSet(this, expected2)
            }
        }

        fun get(index: Int): E {
            return if (status.value == SUCCESS)
                if (index == index1) update1 else update2
            else
                if (index == index1) expected1 else expected2
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}
