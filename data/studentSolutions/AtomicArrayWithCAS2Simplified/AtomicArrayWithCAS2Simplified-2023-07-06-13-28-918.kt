//package day3

import AtomicArrayWithCAS2Simplified.Status.*
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
        val curVal = array[index].value
        val result = if (curVal is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
            when {
                curVal.status.value != SUCCESS && index == curVal.indexF -> curVal.expectedF
                curVal.status.value != SUCCESS && index == curVal.indexS -> curVal.expectedS
                curVal.status.value == SUCCESS && index == curVal.indexF -> curVal.updateF
                curVal.status.value == SUCCESS && index == curVal.indexS -> curVal.updateS
                else -> error("unreachable")
            }
        } else {
            curVal
        }
        @Suppress("UNCHECKED_CAST")
        return result as E
    }

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
        val descriptor = CAS2Descriptor(index1, expected1, update1, index2, expected2, update2)
        descriptor.apply()
        return descriptor.status.value == SUCCESS
    }

    inner class CAS2Descriptor(
        index1: Int,
        expected1: E,
        update1: E,
        index2: Int,
        expected2: E,
        update2: E
    ) {
        val indexF = minOf(index1, index2)
        val indexS = maxOf(index1, index2)

        val expectedF: E
        val expectedS: E
        val updateF: E
        val updateS: E

        init {
            val (ef, es) = if (indexF == index1) expected1 to expected2 else expected2 to expected1
            val (uf, us) = if (indexF == index1) update1 to update2 else update2 to update1

            expectedF = ef
            expectedS = es
            updateF = uf
            updateS = us
        }

        val status = atomic(UNDECIDED)

        fun apply() {
            if (status.value == FAILED) {
                rollbackF()
                return
            }

            if (!installDescriptor(indexF, expectedF)) {
                return
            }

            if (!installDescriptor(indexS, expectedS)) {
                rollbackF()
                return
            }

            if (!status.compareAndSet(UNDECIDED, SUCCESS) && status.value != SUCCESS) {
                array[indexF].compareAndSet(this, expectedF)
//                array[indexS].compareAndSet(this, expectedS)
            } else {
                array[indexF].compareAndSet(this, updateF)
                array[indexS].compareAndSet(this, updateS)
            }
        }

        private fun rollbackF() {
            array[indexF].compareAndSet(this, expectedF)
        }

        private fun installDescriptor(index: Int, expected: E): Boolean {
            while (status.value != SUCCESS) {
                when (val curValueF = array[index].value) {
                    this -> break

                    is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor -> {
                        curValueF.apply()
                        continue
                    }

                    expected -> {
                        if (array[index].compareAndSet(curValueF, this)) {
                            break
                        }
                    }

                    else -> {
                        if (!status.compareAndSet(UNDECIDED, FAILED)) {
                            break
                        }
                        return false
                    }
                }
            }

            return true
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}