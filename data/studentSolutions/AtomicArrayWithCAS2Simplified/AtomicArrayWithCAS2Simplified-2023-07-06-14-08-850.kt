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
        // TODO: the cell can store CAS2Descriptor
        // дескриптор
        val arrayCell = array[index].value
        if (arrayCell is AtomicArrayWithCAS2SingleWriter<*>.CAS2Descriptor) {
            when (arrayCell.status.value) {
                AtomicArrayWithCAS2SingleWriter.Status.SUCCESS -> return arrayCell.update(index) as E
                else -> return arrayCell.expected(index) as E
            }
        }
        // не дескриптор
        return arrayCell as E
    }

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
        // TODO: this implementation is not linearizable,
        // TODO: use CAS2Descriptor to fix it.
        val desc: CAS2Descriptor = if (index1 < index2) {
            CAS2Descriptor(index1, expected1, update1, index2, expected2, update2)
        } else {
            CAS2Descriptor(index2, expected2, update2, index1, expected1, update1)
        }
        desc.apply()
        return desc.status.value === SUCCESS
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

        fun apply2(): Boolean {
            // TODO: install the descriptor, update the status, update the cells.
            // update the status and cells

            // в первом стоит какой то другой дексриптор

            val desc1 = array[index1].value
            if (desc1 is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor && desc1 != this) {
                (desc1 as AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor).apply()
            }


            // while берем значение, если там число то имеем право сказать что чето все фейл, а вот если там дескриптор, то продолжаем помогать..
            if (array[index1].value == this || array[index1].compareAndSet(expected1, this)) {
                val desc2 = array[index2].value

                if (desc2 is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor && desc2 != this) {
                    // первый дескриптор мы поставили, а во втором уже кто-то поставил свое. надо им помочь
                    (desc2 as AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor).apply()
                }

                if (desc2 == this || array[index2].compareAndSet(expected2, this)) {
                    status.compareAndSet(UNDECIDED, SUCCESS)
                    array[index1].compareAndSet(this, update1)
                    array[index2].compareAndSet(this, update2)
                    return true
                } else {
                    status.compareAndSet(UNDECIDED, FAILED)
                    array[index1].compareAndSet(this, expected1)
                    array[index2].compareAndSet(this, expected2)
                }
            } else {
                status.compareAndSet(UNDECIDED, FAILED)
                array[index1].compareAndSet(this, expected1)
            }


//            while (true) {
//                val desc1 = array[index1].value
//                if (desc1 !is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor && desc1 != expected1) {
//                    // там прям число, причем не наше. это точно фейл
//                    return
//                } else if (desc1 !is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor && desc1 == expected1) {
//                    // число но наше. стоит попытаться поставить дескриптор
//                    // тут в результате может стать
//                } else if (desc1 is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor && desc1 == this) {
//                    // наш дескриптор уже стоит. можно двигаться дальше
//                } else {
//                    // не наш дескриптор. придется ему помогать
//                }
//            }


            return false
        }

        fun apply() {
            if (status.value === UNDECIDED) {
                val success = tryInstallDescriptor()
                if (success) {
                    status.compareAndSet(UNDECIDED, SUCCESS)
                } else {
                    status.compareAndSet(UNDECIDED, FAILED)
                }
                updateValues()
            }
        }

        private fun updateValues() {
            if (status.value === SUCCESS) {
                array[index1].compareAndSet(this, update1)
                array[index2].compareAndSet(this, update2)
            } else { // failed
                array[index1].compareAndSet(this, expected1)
                array[index2].compareAndSet(this, expected2)
            }
        }

        private fun tryInstallDescriptor(): Boolean {
            if (!tryInstallDescriptor(index1, expected1)) {
                return false
            }
            if (!tryInstallDescriptor(index2, expected2)) {
                return false
            }
            return true
        }

        private fun tryInstallDescriptor(index: Int, expected: E): Boolean {
            while (true) {
                val curState = array[index].value

                when {
                    curState === this -> {
                        return true
                    }
                    curState is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor -> {
                        curState.apply()
                    }
                    curState === expected -> {
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
            }
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}