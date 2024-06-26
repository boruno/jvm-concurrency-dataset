package day3

import kotlinx.atomicfu.*

private enum class DCSSState {
    UNDECIDED,
    SUCCESS,
    FAILURE,
}

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
        while (true) {
            val v = array[index].value
            if (v is AtomicArrayWithDCSS<*>.DCSSDescriptor) {
                v.apply()
                continue
            }
            @Suppress("UNCHECKED_CAST")
            return v as E?
        }
    }

    fun cas(index: Int, expected: E?, update: E?): Boolean {
        while (true) {
            val v = array[index].value
            when {
                (v === expected) -> {
                    if (array[index].compareAndSet(expected, update))
                        return true
                    continue
                }
                v is AtomicArrayWithDCSS<*>.DCSSDescriptor -> {
                    v.apply()
                    continue
                }
                else -> {
                    return false
                }
            }
        }
    }

    fun dcss(
        index1: Int, expected1: E?, update1: E?,
        index2: Int, expected2: E?
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
        return DCSSDescriptor(index1, expected1, update1, index2, expected2).apply()
    }

    inner class DCSSDescriptor(
        private val index1: Int,
        private val expected1: E?,
        private val update1: E?,
        private val index2: Int,
        private val expected2: E?,
    ) {
        private val state = atomic(DCSSState.UNDECIDED)

        fun apply(): Boolean {
            if (!installDescriptor1())
                return finalize(DCSSState.FAILURE)
            if (!checkExpectation2())
                return finalize(DCSSState.FAILURE)
            return finalize(DCSSState.SUCCESS)
        }

        private fun installDescriptor1(): Boolean {
            while (true) {
                if (state.value != DCSSState.UNDECIDED)
                    return true
                val v = array[index1].value
                when {
                    (v === expected1) -> {
                        if (array[index1].compareAndSet(expected1, this))
                            return true
                        continue
                    }
                    (v === this) -> {
                        return true
                    }
                    v is AtomicArrayWithDCSS<*>.DCSSDescriptor -> {
                        v.apply()
                        continue
                    }
                    else -> {
                        return false
                    }
                }
            }
        }

        private fun checkExpectation2(): Boolean {
            while (true) {
                val v = array[index2].value
                if (v is AtomicArrayWithDCSS<*>.DCSSDescriptor) {
                    v.apply()
                    continue
                }
                return v === expected2
            }
        }

        private fun finalize(s: DCSSState): Boolean {
            state.compareAndSet(DCSSState.UNDECIDED, s)
            return when (state.value) {
                DCSSState.UNDECIDED -> error("Can't be here")
                DCSSState.SUCCESS -> {
                    array[index1].compareAndSet(this, update1)
                    true
                }
                DCSSState.FAILURE -> {
                    array[index1].compareAndSet(this, expected1)
                    false
                }
            }
        }
    }
}