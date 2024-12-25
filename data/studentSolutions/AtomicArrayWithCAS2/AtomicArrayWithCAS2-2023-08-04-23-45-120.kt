@file:Suppress("DuplicatedCode")

//package day3

import kotlinx.atomicfu.*

// This implementation never stores `null` values.
class AtomicArrayWithCAS2<E : Any>(size: Int, initialValue: E) {
    private val array = atomicArrayOfNulls<Any?>(size)

    init {
        // Fill array with the initial value.
        for (i in 0 until size) {
            array[i].value = initialValue
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E? {
        while (true) {
            val value = array[index].value

            if (value is AtomicArrayWithCAS2<*>.CAS2Descriptor) {
                if (value.statusRef.value == Status.SUCCESS) {
                    if (index == value.firstIndex)
                        return value.firstUpdate as E?
                    else if (index == value.secondIndex)
                        return value.secondUpdate as E?
                }

                value.apply()
                continue
            } else
                return value as E?
        }
    }

    fun cas(index: Int, expected: E?, update: E?): Boolean {
        while (true) {
            when (val value = array[index].value) {
                is AtomicArrayWithCAS2<*>.CAS2Descriptor -> value.apply()
                expected -> if (array[index].compareAndSet(expected, update)) return true
                else -> return false
            }
        }
    }

    fun cas2(
        index1: Int, expected1: E?, update1: E?,
        index2: Int, expected2: E?, update2: E?
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }

        val descriptor = CAS2Descriptor(index1, expected1, update1, index2, expected2, update2)
        descriptor.apply()
        return descriptor.statusRef.value == Status.SUCCESS
    }

    private inner class CAS2Descriptor(
        index1: Int, expected1: E?, update1: E?,
        index2: Int, expected2: E?, update2: E?
    ) {
        val statusRef = atomic(Status.UNDECIDED)

        val firstIndex: Int
        private val firstExpected: E?
        val firstUpdate: E?

        val secondIndex: Int
        private val secondExpected: E?
        val secondUpdate: E?

        init {
            if (index1 < index2) {
                firstIndex = index1
                firstExpected = expected1
                firstUpdate = update1

                secondIndex = index2
                secondExpected = expected2
                secondUpdate = update2
            } else {
                firstIndex = index2
                firstExpected = expected2
                firstUpdate = update2

                secondIndex = index1
                secondExpected = expected1
                secondUpdate = update1
            }
        }

        fun apply() {
            while (true) {
                when (statusRef.value) {
                    Status.SUCCESS -> return commit()
                    Status.FAILED -> return rollback()
                    Status.UNDECIDED -> {
                        when (val value = array[firstIndex].value) {
                            this -> break
                            is AtomicArrayWithCAS2<*>.CAS2Descriptor -> value.apply()
                            firstExpected -> if (array[firstIndex].compareAndSet(firstExpected, this)) break
                            else -> {
                                statusRef.value = Status.FAILED
                                rollback()
                            }
                        }
                    }
                }
            }

            while (true) {
                when (statusRef.value) {
                    Status.SUCCESS -> return commit()
                    Status.FAILED -> return rollback()
                    Status.UNDECIDED -> {
                        when (val value = array[secondIndex].value) {
                            this -> break
                            is AtomicArrayWithCAS2<*>.CAS2Descriptor -> value.apply()
                            secondExpected -> if (array[secondIndex].compareAndSet(secondExpected, this)) break
                            else -> {
                                statusRef.value = Status.FAILED
                                rollback()
                            }
                        }
                    }
                }
            }

            statusRef.value = Status.SUCCESS
            commit()
        }

        private fun commit() {
            array[firstIndex].compareAndSet(this, firstUpdate)
            array[secondIndex].compareAndSet(this, secondUpdate)
        }

        private fun rollback() {
            array[firstIndex].compareAndSet(this, firstExpected)
            array[secondIndex].compareAndSet(this, secondExpected)
        }
    }


    private enum class Status {
        UNDECIDED, FAILED, SUCCESS
    }
}
