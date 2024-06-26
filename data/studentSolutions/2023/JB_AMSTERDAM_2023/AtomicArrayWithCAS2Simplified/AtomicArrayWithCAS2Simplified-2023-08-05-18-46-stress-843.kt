package day3

import day3.AtomicArrayWithCAS2Simplified.Status.*
import day3.AtomicArrayWithCAS2SingleWriter.*
import kotlinx.atomicfu.*
import kotlin.math.min


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
        is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor -> {
            val updated  = if (cell.index1 == index) cell.update1   else cell.update2
            val expected = if (cell.index1 == index) cell.expected1 else cell.expected2
            if (cell.status.value === SUCCESS) { updated } else { expected }
        }
        else -> cell
    } as E

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
        val descriptor =
            if (index1 < index2) {
                CAS2Descriptor(
                index1 = index1, expected1 = expected1, update1 = update1,
                index2 = index2, expected2 = expected2, update2 = update2)
            } else {
                    CAS2Descriptor(
                        index1 = index2, expected1 = expected2, update1 = update2,
                        index2 = index1, expected2 = expected1, update2 = update1)
            }
        descriptor.apply()
        return descriptor.status.value === SUCCESS
    }

    inner class CAS2Descriptor(
        public val index1: Int,
        public val expected1: E,
        public val update1: E,
        public val index2: Int,
        public val expected2: E,
        public val update2: E
    ) {
        val status = atomic(UNDECIDED)

        fun apply() {
            while (true) {
                var old1 = array[index1].value
                if (old1 == this) {
                    break
                }
                if (old1 is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
                    if (old1.status.value == SUCCESS) {
                        array[old1.index1].compareAndSet(old1, old1.update1)
                        array[old1.index2].compareAndSet(old1, old1.update2)
                    } else if (old1.status.value == UNDECIDED) {
                        old1.apply()
                    } else if (old1.status.value == FAILED) {
                        array[old1.index1].compareAndSet(old1, old1.expected1)
                        array[old1.index2].compareAndSet(old1, old1.expected2)
                    }
                    continue
                } else {
                    // CASE: old1 is a value
                    if (array[index1].compareAndSet(expected1, this)) {
                        // i.e. SUCCESS go to second value
                        break
                    } else {
                        // either array[index1] (=?= old1) is not equal to expected
                        //   or someone pushed there another descriptor
                        if (this.expected1 != old1) {
                            // this.status.value = FAILED
                            status.compareAndSet(UNDECIDED, FAILED)
                            return
                        } else { // or case
                            continue
                        }
                    }
                }
            }

            while (true) {
                var old2 = array[index2].value
                if (old2 == this) { break }
                if (old2 is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
                    if (old2.status.value == SUCCESS) {
                        array[old2.index1].compareAndSet(old2, old2.update1)
                        array[old2.index2].compareAndSet(old2, old2.update2)
                    } else if (old2.status.value == UNDECIDED) {
                        old2.apply()
                    } else if (old2.status.value == FAILED) {
                        array[old2.index1].compareAndSet(old2, old2.expected1)
                        array[old2.index2].compareAndSet(old2, old2.expected2)
                    }
                    continue
                } else {
                    // CASE: old2 is a value
                    if (array[index2].compareAndSet(expected2, this)) {
                        // i.e. SUCCESS go to second value
                        break
                    } else {
                        // either array[index2] (=?= old2) is not equal to expected
                        //   or someone pushed there another descriptor
                        if (this.expected2 != old2) {
                            // this.status.value = FAILED
                            status.compareAndSet(UNDECIDED, FAILED)
                            // array[index1].compareAndSet(this, old1)
                            array[index1].compareAndSet(this, expected1)
                            return
                        } else { // or case
                            continue
                        }
                    }
                }
            }

            while (true) {
                if (status.compareAndSet(UNDECIDED, SUCCESS) || status.value == SUCCESS) {
                    array[index1].compareAndSet(this, update1)
                    array[index2].compareAndSet(this, update2)
                    return
                }
                if (status.value == FAILED) {
                    array[index1].compareAndSet(this, this.expected1)
                    array[index2].compareAndSet(this, this.expected2)
                    return
                }
            }
        }

//        fun apply() {
//            // TODO: Install the descriptor, update the status, and update the cells;
//            // TODO: create functions for each of these three phases.
//            var old1 = array[index1].value
//
//            while (true) {
//                    if (old1 is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
//                        if (old1 == this) { break }
//                        if (old1.status.value == SUCCESS) {
//                            array[index1].compareAndSet(old1, old1.update1)
//                            array[index2].compareAndSet(old1, old1.update2)
//                        } else if (old1.status.value == UNDECIDED && old1 != this) {
//                            old1.apply()
//                        }
//                        old1 = array[index1].value
//                        continue
//                    }
//                if (array[index1].compareAndSet(expected1, this)) { break }
//                // if (array[index1].value == update1) { break }
//                if (status.value == SUCCESS || array[index1].value == this) { break }
//                // status.getAndSet(FAILED)
//                status.compareAndSet(UNDECIDED, FAILED)
//                return
//            }
//            var old2 = array[index2].value
//            while (true) {
//                if (old2 == this) { break }
//                    if (old2 is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
//                        if (old2.status.value == SUCCESS) {
//                            array[index1].compareAndSet(old2, old2.update1)
//                            array[index2].compareAndSet(old2, old2.update2)
//                        } else if (old2.status.value == UNDECIDED && old2 != this) {
//                            old2.apply()
//                        }
//                        old2 = array[index2].value
//                        continue
//                    }
//                if (status.value == SUCCESS ||
//                    status.value == FAILED ||
//                    array[index2].compareAndSet(expected2, this) ||
//                    array[index2].value == this) { break }
//                // if () { break }
//                // if (array[index2].value == update2) { break }
//                array[index1].compareAndSet(this, this.expected1)
//                status.compareAndSet(UNDECIDED, FAILED)
//                return
//            }
//
//            if (status.compareAndSet(UNDECIDED, SUCCESS)) {
//
//                array[index1].compareAndSet(this, update1)
//                array[index2].compareAndSet(this, update2)
//            }
//        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}