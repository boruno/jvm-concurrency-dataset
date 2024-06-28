package day3

import day3.AtomicArrayWithCAS2Simplified.Status.*
import kotlinx.atomicfu.*
import java.lang.IllegalStateException
import kotlin.math.exp


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
        while (true) {
            val e = array[index].value
            if (e is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
                e.apply()
            } else {
                e as E
            }
        }
    }

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }

        val ds = if (index1 > index2) {
            CAS2Descriptor(index2, expected2, update2, index1, expected1, update1)
        } else {
            CAS2Descriptor(index1, expected1, update1, index2, expected2, update2)
        }

        while (ds.status.value == UNDECIDED) {
            ds.apply()
        }
        return ds.status.value == SUCCESS
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
            // TODO: install the descriptor, update the status, update the cells.
           if (install()) {
               status.compareAndSet(UNDECIDED, SUCCESS)
           } else {
               status.compareAndSet(UNDECIDED, FAILED)
           }
            updateValues()
        }

        fun install(): Boolean {
            return install(index1, expected1) && install(index2, expected2)
        }

        fun install(i: Int, expected: E): Boolean {
            val e = array[i].value
            return when {
                e === this -> true
                e is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor -> {
                    apply()
                    install(i, expected)
                }
                e === expected -> if (array[i].compareAndSet(expected, this)) true else install(i, expected)
                else -> false
            }
        }
        fun updateValues() {
            when (status.value) {
                SUCCESS -> {
                    array[index1].compareAndSet(this, update1)
                    array[index2].compareAndSet(this, update2)
                }
                FAILED -> {
                    array[index1].compareAndSet(this, expected1)
                    array[index2].compareAndSet(this, expected2)
                }
                else -> throw IllegalStateException()
            }
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}