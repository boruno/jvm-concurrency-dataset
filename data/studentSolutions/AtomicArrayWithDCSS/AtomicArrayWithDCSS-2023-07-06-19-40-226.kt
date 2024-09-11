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
        return array[index].value as E?
    }

    fun cas(index: Int, expected: E?, update: E?): Boolean {
        while (true) {
            val state = array[index].value
            when {
                (state is AtomicArrayWithDCSS<*>.DCSSDescriptor) -> {
                    state.apply()
                }
                (state === expected) -> {
                    return array[index].compareAndSet(expected, update)
                }
                else -> {
                    return false
                }
            }
        }
//        // TODO: the cell can store a descriptor
//        return array[index].compareAndSet(expected, update)
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
        val dcssDescriptor = DCSSDescriptor(index1, expected1, update1, index2, expected2)
        if (!dcssDescriptor.tryInstall())
            return false
        dcssDescriptor.apply()
        return dcssDescriptor.status.value === Status.SUCCESS
    }

    inner class DCSSDescriptor(
        val index1: Int,
        val expected1: E?,
        val update1: E?,
        val index2: Int,
        val expected2: E?
    ) {
        val status = atomic(Status.UNDECIDED)

        fun tryInstall(): Boolean {
            while (true) {
                val state1 = array[index1].value
                when {
                    (state1 === this) -> {
                        return true
                    }
                    (state1 is AtomicArrayWithDCSS<*>.DCSSDescriptor) -> {
                        state1.apply()
                    }
                    (state1 === expected1) -> {
                        if (array[index1].compareAndSet(expected1, this)) {
                            return true
                        }
                        break
                    }
                    else -> {
                        break
                    }
                }
            }
            status.compareAndSet(Status.UNDECIDED, Status.FAILED)
            return false
        }
        fun apply() {
            if (status.value === Status.UNDECIDED) {
                if (array[index2].value === expected2) {
                    status.compareAndSet(Status.UNDECIDED, Status.SUCCESS)
                } else {
                    status.compareAndSet(Status.UNDECIDED, Status.SUCCESS)
                }
            }

            require(status.value !== Status.UNDECIDED)

            if (status.value === Status.SUCCESS) {
                array[index1].compareAndSet(this, update1)
            } else {
                array[index2].compareAndSet(this, expected1)
            }
        }

    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }

}