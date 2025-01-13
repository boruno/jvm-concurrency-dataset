//package day3

import AtomicArrayWithCAS2Simplified.Status.*
import kotlinx.atomicfu.*
import java.lang.IllegalStateException


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
        val cell = array[index].value

        return if (cell is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
            val casData = when (index) {
                cell.casDataA.index -> cell.casDataA as CasData<E>
                cell.casDataB.index -> cell.casDataB as CasData<E>
                else -> throw IllegalStateException()
            }

            when (cell.status.value) {
                UNDECIDED, FAILED -> casData.expected
                SUCCESS -> casData.updated
            }
        } else {
            cell as E
        }

    }

    class CasData<E>(
        val index: Int,
        val expected: E,
        val updated: E,
    )

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
        // TODO: this implementation is not linearizable,
        // TODO: use CAS2Descriptor to fix it.
        // TODO: Note that only one thread can call CAS2!
        val casData1 = CasData(index1, expected1, update1)
        val casData2 = CasData(index2, expected2, update2)

        val (a, b) = if (casData1.index < casData2.index) {
            casData1 to casData2
        } else {
            casData2 to casData1
        }

        val descriptor = CAS2Descriptor(
            a.index, a.expected, a.updated,
            b.index, b.expected, b.updated,
        )

        // let's apply updates in a consistent order
        while (true) {
            val current = array[a.index].value

            if (current is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
                val result = current.apply()
                if (current == this) {
                    return result
                }
                continue // try again
            } else if (a.expected != current) {
                return false
            } else {
                if (array[a.index].compareAndSet(a.expected, this)) {
                    return descriptor.apply()
                } else {
                    continue // try again
                }
            }
        }
    }

    inner class CAS2Descriptor(
        index1: Int,
        expected1: E,
        update1: E,
        index2: Int,
        expected2: E,
        update2: E
    ) {
        val status = atomic(UNDECIDED)

        val casDataA = CasData(index1, expected1, update1)
        val casDataB = CasData(index2, expected2, update2)

        fun apply(): Boolean {
            // at this moment we have already applied to cell A
            val b = casDataB

            while (true) {
                val current = array[b.index].value

                if (current is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
                    val result = current.apply() // todo: can this be our descriptor?
                    if (current == this) {
                        return result
                    }
                    continue // try again
                } else if (b.expected != current) {
                    status.compareAndSet(UNDECIDED, FAILED)
                    rollbackValues()
                    return false
                } else {
                    if (array[b.index].compareAndSet(b.expected, this)) {
                        status.compareAndSet(UNDECIDED, SUCCESS)
                        applyValues()
                        return true
                    } else {
                        continue // try again
                    }
                }
            }
        }

        private fun applyValues() {
            listOf(casDataA, casDataB).forEach {
                array[it.index].value = it.updated
            }
        }
        private fun rollbackValues() {
            val a = casDataA
            array[a.index].value = a.expected
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}