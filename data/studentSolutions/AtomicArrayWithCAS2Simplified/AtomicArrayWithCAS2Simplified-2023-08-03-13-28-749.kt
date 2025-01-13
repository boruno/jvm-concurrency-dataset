//package day3

import AtomicArrayWithCAS2Simplified.Status.*
import kotlinx.atomicfu.*
import kotlin.math.max
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

    fun get(index: Int): E {
        val value = array[index].value

        return if (value is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
            when (index) {
                value.index1 -> if (value.status.value == SUCCESS) value.update1 as E else value.expected1 as E
                value.index2 -> if (value.status.value == SUCCESS) value.update2 as E else value.expected2 as E
                else -> error("Wrong index")
            }
        } else {
            value as E
        }
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
        val index1: Int,
        val expected1: E,
        val update1: E,
        val index2: Int,
        val expected2: E,
        val update2: E
    ) {
        val status = atomic(UNDECIDED)

        fun apply() {
            val installStatus = install()
            updateStatus(installStatus)
            updateCells()
        }

        fun install(): Boolean {
//            while(true) {
//                val idx1Value = array[index1].value
//
//
//            }
            return if (index1 < index2) {
                installToIndx(index1, expected1) && installToIndx(index2, expected2)
            } else {
                installToIndx(index2, expected2) && installToIndx(index1, expected1)
            }

            //return installToIndx(index1, expected1) && installToIndx(index2, expected2)
            //if (index1Installed) return installToIndx(index1, expected1)

//            while (true) {
//
//                var index1Installed = array[index1].compareAndSet(expected1, this)
//
//                if (!index1Installed) {
//                    val indx1Val = array[index1].value
//                    if (indx1Val is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
//                        if (indx1Val == this)
//                            index1Installed = true
//                        else {
//                            indx1Val.apply()
//                            continue
//                        }
//                    } else {
//                        index1Installed = false
//                    }
//                }
//                if (!index1Installed) return false
//
//                var index2Installed = array[index2].compareAndSet(expected2, this)
//
//                if (!index2Installed) {
//                    val indx2Val = array[index2].value
//
//                    if (indx2Val is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
//                        if (indx2Val == this)
//                            index2Installed = true
//                        else {
//                            indx2Val.apply()
//                            continue
//                        }
//
//                    } else {
//                        index2Installed = false
//                    }
//
//                }
//
//                return index2Installed
//            }
        }

        private fun installToIndx(index: Int, expected: E): Boolean {
            while (true) {
                var indexInstalled = array[index].compareAndSet(expected, this)

                if (!indexInstalled) {
                    val indxVal = array[index].value
                    if (indxVal is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
                        if (indxVal == this)
                            indexInstalled = true
                        else {
                            indxVal.apply()
                            continue
                        }
                    } else {
                        indexInstalled = false
                    }
                }
                return indexInstalled
            }
        }


        private fun updateStatus(installed: Boolean) {
            if (installed) {
                this.status.compareAndSet(UNDECIDED, SUCCESS)
            } else {
                this.status.compareAndSet(UNDECIDED, FAILED)
            }
        }

        private fun updateCells() {
            if (this.status.value == SUCCESS) {
                array[index1].compareAndSet(this, update1)
                array[index2].compareAndSet(this, update2)
            }
            if (this.status.value == FAILED) {
                array[index1].compareAndSet(this, expected1)
                array[index2].compareAndSet(this, expected2)
            }
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}