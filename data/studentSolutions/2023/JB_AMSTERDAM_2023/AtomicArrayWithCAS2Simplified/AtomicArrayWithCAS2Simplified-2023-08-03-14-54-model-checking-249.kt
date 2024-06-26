package day3

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

    fun get(index: Int): E = when (val cell = array[index].value) {
        is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor -> cell.get(index) as E
        else -> cell as E
    }

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
        val descriptor = CAS2Descriptor(
            index1 = index1, expected1 = expected1, update1 = update1,
            index2 = index2, expected2 = expected2, update2 = update2
        )
        descriptor.apply()
        return descriptor.status.value === SUCCESS
    }

    inner class CAS2Descriptor(
        index1: Int, expected1: E, update1: E, index2: Int, expected2: E, update2: E
    ) {
        val status = atomic(UNDECIDED)

        private val reversed = index1 < index2
        private val indexOne = if (reversed) index1 else index2
        private val indexTwo = if (reversed) index2 else index1
        private val expectedOne = if (reversed) expected1 else expected2
        private val expectedTwo = if (reversed) expected2 else expected1
        private val updateOne = if (reversed) update1 else update2
        private val updateTwo = if (reversed) update2 else update1

        fun install1() {
            while (true) {
                val c = array[indexOne].value
                when {
                    array[indexOne].compareAndSet(expectedOne, this) -> install2()
                    c is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor -> c.install2()
                    else -> {
                        return
                    }
                }
            }
        }

        fun install2() {
            while (true) {
                val c = array[indexTwo].value
                when {
                    array[indexTwo].compareAndSet(expectedTwo, this) -> physicalMutation()
                    c is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor -> c.physicalMutation()
                    else -> {
                        if (status.compareAndSet(UNDECIDED, FAILED)) {
                            array[indexOne].compareAndSet(this, expectedOne)
                            return
                        }
                    }
                }
            }
        }

        fun physicalMutation() {
            status.compareAndSet(UNDECIDED, SUCCESS)
            array[indexOne].compareAndSet(this, updateOne)
            array[indexTwo].compareAndSet(this, updateTwo)
        }

        fun apply() {
            install1()
        }

        fun get(index: Int): E = when (status.value) {
            UNDECIDED, FAILED -> when (index) {
                indexOne -> expectedOne
                indexTwo -> expectedTwo
                else -> error("descriptor must contain $index")
            }

            SUCCESS -> when (index) {
                indexOne -> updateOne
                indexTwo -> updateTwo
                else -> error("descriptor must contain $index")
            }
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}