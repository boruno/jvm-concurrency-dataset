@file:Suppress("DuplicatedCode")

//package day3

import kotlinx.atomicfu.*
import java.lang.IllegalStateException

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
            is AtomicArrayWithCAS2<*>.DCSS -> {
                if (e.status.value == Status.SUCCESS) {
                    getValueFromCas(e.casDescriptor, index) as E
                }
                else e.expected as E
            }
            is AtomicArrayWithCAS2<*>.CAS2Descriptor -> getValueFromCas(e, index) as E
            else -> e as E
        }
    }

    fun getValueFromCas(casDescriptor: AtomicArrayWithCAS2<*>.CAS2Descriptor, index: Int): Any {
        return if (casDescriptor.status.value == Status.SUCCESS) {
            if (index == casDescriptor.index1) casDescriptor.update1
            else casDescriptor.update2
        } else if (index == casDescriptor.index1) {
            casDescriptor.expected1
        } else {
            casDescriptor.expected2
        }
    }

    fun cas(index: Int, expected: E, update: E): Boolean {
        // TODO: the cell can store a descriptor
        return when (val e = array[index].value) {
            expected ->
                if (array[index].compareAndSet(expected, update)) return true
                else cas(index, expected, update)
            is AtomicArrayWithCAS2<*>.DCSS -> {
                e.applyDcss()
                cas(index, expected, update)
            }
            is AtomicArrayWithCAS2<*>.CAS2Descriptor -> {
                e.applyCas()
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

        ds.applyCas()
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

        fun applyCas() {
            if (status.value == Status.UNDECIDED) {
                if (installCas()) {
                    status.compareAndSet(Status.UNDECIDED, Status.SUCCESS)
                } else {
                    status.compareAndSet(Status.UNDECIDED, Status.FAILED)
                }
            }
            // TODO: install the descriptor, update the status, update the cells.
            updateValuesCas()
        }

        fun installCas(): Boolean {
            return installCas(index1, expected1) && installCas(index2, expected2)
        }

        fun installCas(i: Int, expected: E): Boolean {
            while (true) {
                val e = array[i].value
                when {
                    e == this -> return true
                    e == expected -> return dcss(i, expected, this)
                    e is AtomicArrayWithCAS2<*>.CAS2Descriptor -> e.applyCas()
                    e is AtomicArrayWithCAS2<*>.DCSS -> e.applyDcss()
                    else -> return false
                }
            }
        }
        fun updateValuesCas() {
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
                 cas: CAS2Descriptor): Boolean {
            val dcss = DCSS(index, expected, cas, Status.UNDECIDED)
            dcss.applyDcss()
            return dcss.status.value == Status.SUCCESS
        }
    }

    inner class DCSS(
        val index: Int,
        val expected: E?,
        val casDescriptor: CAS2Descriptor,
        val expectedStatus: Status) {

        val status = atomic(Status.UNDECIDED)

        fun applyDcss() {
            if (status.value == Status.UNDECIDED) {
                if (installDescriptorDcss() && casDescriptor.status.value == expectedStatus) {
                    status.compareAndSet(Status.UNDECIDED, Status.SUCCESS)
                } else {
                    status.compareAndSet(Status.UNDECIDED, Status.FAILED)
                }
            }
            updateValueDcss()
        }

        private fun installDescriptorDcss(): Boolean {
            while (true) {
                val e = array[index].value
                when {
                    e == this -> return true
                    e is AtomicArrayWithCAS2<*>.DCSS -> e.applyDcss()
                    e == expected && status.value == Status.UNDECIDED -> if (array[index].compareAndSet(expected, this)) return true
                    else -> return false
                }
            }
        }

        private fun updateValueDcss() {
            when (status.value) {
                Status.SUCCESS -> array[index].compareAndSet(this, casDescriptor)
                Status.FAILED -> array[index].compareAndSet(this, expected)
                else -> throw IllegalStateException()
            }
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}