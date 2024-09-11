package day3

import day3.AtomicArrayWithCAS2Simplified.Status.*
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
        // TODO: the cell can store CAS2Descriptor
        val cell = array[index].value

        return if (cell is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
            cell as AtomicArrayWithCAS2Simplified<E>.CAS2Descriptor

            val (a, b) = cell.aToB()

            val casData = when (index) {
                a.index -> a
                b.index -> b
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
        val descriptor = CAS2Descriptor(
            index1, expected1, update1,
            index2, expected2, update2,
        )

        // let's apply updates in a consistent order
        while (true) {
            val (a, b) = descriptor.aToB()

            val current = array[a.index].value

            if (current is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
                current.apply()
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

        private val casData1 = CasData(index1, expected1, update1)
        private val casData2 = CasData(index2, expected2, update2)

        fun aToB() = if (casData1.index < casData2.index) {
            casData1 to casData2
        } else {
            casData2 to casData1
        }

        fun apply(): Boolean {
            // at this moment we have already applied to cell A
            val (_, b) = aToB()

            while (true) {
                val current = array[b.index].value

                if (current is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
                    current.apply() // todo: can this be our descriptor?
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
            listOf(casData1, casData2).forEach {
                array[it.index].value = it.updated
            }
        }
        private fun rollbackValues() {
            val (a, _) = aToB()
            array[a.index].value = a.expected
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}