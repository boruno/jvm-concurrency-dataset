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

    fun get(index: Int): E {
        // TODO: the cell can store CAS2Descriptor
        val value = array[index].value
        if (value !is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) return value as E

        val status = value.status.value
        if (status == UNDECIDED || status == FAILED) return if (value.index1 == index) value.expected1 as E else value.expected2 as E

        value.apply()

        return if (value.index1 == index) value.update1 as E else value.update2 as E
    }

    fun cas2(
        index1arg: Int, expected1arg: E, update1arg: E, index2arg: Int, expected2arg: E, update2arg: E
    ): Boolean {
        val index1: Int
        val expected1: E
        val update1: E

        val index2: Int
        val expected2: E
        val update2: E

        if (index1arg > index2arg) {
            index1 = index2arg
            expected1 = expected2arg
            update1 = update2arg

            index2 = index1arg
            expected2 = expected1arg
            update2 = update1arg
        } else {
            index1 = index1arg
            expected1 = expected1arg
            update1 = update1arg

            index2 = index2arg
            expected2 = expected2arg
            update2 = update2arg
        }


        val caS2Descriptor = CAS2Descriptor(index1, expected1, update1, index2, expected2, update2)
        caS2Descriptor.apply()
        return caS2Descriptor.status.value == SUCCESS


//        require(index1 != index2) { "The indices should be different" }
        // TODO: this implementation is not linearizable,
        // TODO: use CAS2Descriptor to fix it.

    }


    inner class CAS2Descriptor(
        val index1: Int, val expected1: E, val update1: E, val index2: Int, val expected2: E, val update2: E
    ) {
        val status = atomic(UNDECIDED)

        fun apply() {
            while (true) {
                val localStatus = status.value

                val fst = array[index1].value
                val snd = array[index2].value

                if (fst !is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor && fst != expected1 && snd !is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor && snd != expected2) {
                    status.compareAndSet(UNDECIDED, FAILED)
                }

                if (localStatus == SUCCESS) {
                    array[index1].compareAndSet(this, update1)
                    array[index2].compareAndSet(this, update2)
                    return
                }

                if (localStatus == FAILED) {
                    array[index1].compareAndSet(this, expected1)
                    array[index2].compareAndSet(this, expected2)
                    return
                }

                if (!array[index1].compareAndSet(expected1, this)) {
                    val v = array[index1].value
                    if (this != v) {
                        if (v is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
                            v.apply()
                        }
                        continue
                    }
                }

                if (!array[index2].compareAndSet(expected2, this)) {
                    val v = array[index2].value
                    if (this != v) {
                        if (v is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
                            v.apply()
                        }
                        continue
                    }
                }

                status.compareAndSet(UNDECIDED, SUCCESS)
            }

        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}