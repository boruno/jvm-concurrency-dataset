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
        val state = array[index].value
        if (state is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
            val cas = state as AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor
            if (cas.status.value == SUCCESS) {
                if (index == cas.index1) {return cas.update1 as E}
                else {return cas.update2 as E}
            } else {
                if (index == cas.index1) {return cas.expected1 as E}
                else {return cas.expected2 as E}
            }
        } else {
            return state as E
        }
    }

//    private fun installDescriptor(index: Int, expected: E, descriptor: CAS2Descriptor): Boolean {
//        while (true) {
//            if (array[index].compareAndSet(expected, descriptor)) {
//                return true
//            } else {
//                val descriptor1 = array[index].value
//                if (descriptor1 is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
//                    // we need to help
//                    descriptor1.apply()
//                } else {
//                    // the value is already something unexpected, thus we fail
//                    return false
//                }
//            }
//        }
//    }

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
        // TODO: this implementation is not linearizable,
        // TODO: use CAS2Descriptor to fix it.
        val descriptor = if (index1 < index2) {
            CAS2Descriptor(index1, expected1, update1, index2, expected2, update2)
        } else {
            CAS2Descriptor(index2, expected2, update2, index1, expected1, update1)
        }
        descriptor.apply()
        return descriptor.status.value == SUCCESS


//        var installedIn1 = installDescriptor(index1, expected1, descriptor);
//        var installedIn2 = false;
//
//        if (!installedIn1) {
//            return false
//        }
//        installedIn2 = installDescriptor(index2, expected2, descriptor)
//
//        if (!installedIn2) {
//            descriptor.status.compareAndSet(UNDECIDED, FAILED)
//        }
//
//        descriptor.apply()
//        return descriptor.status.value == SUCCESS
    }

    inner class CAS2Descriptor(
         val index1: Int,
         val expected1: E,
         val update1: E,
         val index2: Int,
         val expected2: E,
         val update2: E
    ) {
        val status = atomic(UNDECIDED)

        private fun installDescriptor(index: Int, expected: E): Boolean {
            val state = array[index].value
            while (true) {
                when {
                    (state === this) -> {
                        return true
                    }
                    (state is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) -> {
                        state.apply()
                    }
                    (state === expected) -> {
                        if (array[index].compareAndSet(expected, this)) {
                            return true
                        } else {
                            continue
                        }
                    }
                    else -> {
                        return false
                    }
                }
//                if (array[index].compareAndSet(expected, descriptor)) {
//                    return true
//                } else {
//                    val descriptor1 = array[index].value
//                    if (descriptor1 is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
//                        // we need to help
//                        descriptor1.apply()
//                    } else {
//                        // the value is already something unexpected, thus we fail
//                        return false
//                    }
//                }
            }
        }

        private fun tryInstallDescriptors() {
            installDescriptor(index1, expected1)
            installDescriptor(index2, expected2)
        }

        private fun updateValues() {
            array[index1].compareAndSet(this, update1)
            array[index2].compareAndSet(this, update2)

        }



        fun apply() {
            // TODO: install the descriptor, update the status, update the cells.
            if (status.value === UNDECIDED) {
                tryInstallDescriptors()
            }
            updateValues()


//            status.compareAndSet(UNDECIDED, SUCCESS)
//
//            if (status.value == FAILED) {
//                array[index1].compareAndSet(this, expected1)
//                return
//            }
//
//            if (index1 < index2) {
//                array[index1].compareAndSet(this, update1)
//                array[index2].compareAndSet(this, update2)
//            } else {
//                array[index2].compareAndSet(this, update2)
//                array[index1].compareAndSet(this, update1)
//            }
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}