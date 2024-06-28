@file:Suppress("DuplicatedCode")

package day3

import kotlinx.atomicfu.*
import java.lang.IllegalStateException
import javax.swing.undo.UndoableEditSupport
import kotlin.math.exp

// This implementation never stores `null` values.
class AtomicArrayWithCAS2<E : Any>(size: Int, initialValue: E) {
    private val array = atomicArrayOfNulls<Any?>(size)

    init {
        // Fill array with the initial value.
        for (i in 0 until size) {
            array[i].value = initialValue
        }
    }

    fun get(index: Int): E? {
        // TODO: the cell can store a descriptor
        return when (val e = array[index].value) {
            is AtomicArrayWithDCSS<*>.DCSS -> {
                if (e.status.value == AtomicArrayWithDCSS.Status.SUCCESS) e.update1 as E
                else e.expected1 as E
            }
            is AtomicArrayWithCAS2<*>.CAS2Descriptor -> {
                if (e.status.value == Status.SUCCESS) {
                    if (index == e.index1) e.update1 as E
                    else e.update2 as E
                } else if (index == e.index1) {
                    e.expected1 as E
                } else {
                    e.expected2 as E
                }
            }
            else -> e as E
        }
    }

    fun cas(index: Int, expected: E, update: E): Boolean {
        // TODO: the cell can store a descriptor
        return when (val e = array[index].value) {
            expected ->
                if (array[index].compareAndSet(expected, update)) return true
                else cas(index, expected, update)
            is AtomicArrayWithDCSS<*>.DCSS -> {
                e.apply()
                cas(index, expected, update)
            }
            is AtomicArrayWithCAS2<*>.CAS2Descriptor -> {
                e.apply()
                cas(index, expected, update)
            }
            else -> false
        }
    }

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
        // TODO: this implementation is not linearizable,
        // TODO: Store a CAS2 descriptor in array[index1].
        val ds = if (index1 > index2) {
            CAS2Descriptor(index2, expected2, update2, index1, expected1, update1)
        } else {
            CAS2Descriptor(index1, expected1, update1, index2, expected2, update2)
        }

        ds.apply()
        return ds.status.value == Status.SUCCESS
    }

    inner class CAS2Descriptor(
        val index1: Int,
        val expected1: E,
        val update1: E,
        val index2: Int,
        val expected2: E,
        val update2: E
    ) {
        val status = atomic(Status.UNDECIDED)

        fun apply() {
            if (status.value == Status.UNDECIDED) {
                if (install()) {
                    status.compareAndSet(Status.UNDECIDED, Status.SUCCESS)
                } else {
                    status.compareAndSet(Status.UNDECIDED, Status.FAILED)
                }
            }
            // TODO: install the descriptor, update the status, update the cells.
            updateValues()
        }

        fun install(): Boolean {
            return install(index1, expected1) && install(index2, expected2)
        }

        fun install(i: Int, expected: E): Boolean {
            while (true) {
                val e = array[i].value
                when {
                    e == this -> return true
                    dcss(i, expected, this, Status.UNDECIDED, this) -> return true
                    e == expected && status.value == Status.UNDECIDED -> if (array[i].compareAndSet(expected, this)) return true
                    e is AtomicArrayWithCAS2<*>.CAS2Descriptor -> e.apply()
                    e is AtomicArrayWithCAS2<*>.DCSS -> e.apply()
                    else -> return false
                }
            }
        }
        fun updateValues() {
            when (status.value) {
                Status.SUCCESS -> {
                    array[index1].compareAndSet(this, update1)
                    array[index2].compareAndSet(this, update2)
                }
                Status.FAILED -> {
                    array[index1].compareAndSet(this, expected1)
                    array[index2].compareAndSet(this, expected2)
                }
                else -> throw IllegalStateException()
            }
        }

        fun dcss(index: Int, expected: E,
                 cas: CAS2Descriptor, expectedStatus: Status,
                 update: Any): Boolean {
            val dcss = DCSS(index, expected, cas, expectedStatus, update)
            dcss.apply()
            return dcss.status.value == Status.SUCCESS
        }
    }

    inner class DCSS(
        val index: Int,
        val expected: E?,
        val cas: CAS2Descriptor,
        val expectedStatus: Status,
        val update: Any) {

        val status = atomic(Status.UNDECIDED)

        fun apply() {
            if (status.value == Status.UNDECIDED) {
                if (installDescriptor() && cas.status.value == expectedStatus) {
                    status.compareAndSet(Status.UNDECIDED, Status.SUCCESS)
                } else {
                    status.compareAndSet(Status.UNDECIDED, Status.FAILED)
                }
            }
            updateValue()
        }

        private fun installDescriptor(): Boolean {
            while (true) {
                when (val e = array[index].value) {
                    this -> return true
                    expected -> if (array[index].compareAndSet(expected, this)) return true
                    is AtomicArrayWithDCSS<*>.DCSS -> { e.apply() }
                    else -> return false
                }
            }
        }

        private fun updateValue() {
            when (status.value) {
                Status.SUCCESS -> array[index].compareAndSet(this, update)
                Status.FAILED -> array[index].compareAndSet(this, expected)
                else -> throw IllegalStateException()
            }
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}