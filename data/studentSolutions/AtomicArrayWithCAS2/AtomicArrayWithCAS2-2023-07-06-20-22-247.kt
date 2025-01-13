@file:Suppress("DuplicatedCode")

//package day3

import AtomicArrayWithCAS2.Status.*
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls

// This implementation never stores `null` values.
class AtomicArrayWithCAS2<E : Any>(size: Int, initialValue: E) {
    private val array = atomicArrayOfNulls<Any?>(size)

    init {
        // Fill array with the initial value.
        for (i in 0 until size) {
            array[i].value = initialValue
        }
    }

    fun get(index: Int): E {
        val v = array[index].value
        return if (v is Descriptor<*>) {
            v.getValue(index) as E
        } else {
            v as E
        }
    }

    fun cas(index: Int, expected: E, update: E): Boolean {
        while (true) {
            when (val v = array[index].value) {
                is Descriptor<*> -> v.help()
                expected -> {
                    if (array[index].compareAndSet(expected, update)) {
                        return true
                    }
                }
                else -> return false
            }
        }
    }

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
        val descriptor = if (index1 < index2) CAS2Descriptor(index1, expected1, update1, index2, expected2, update2)
        else CAS2Descriptor(index2, expected2, update2, index1, expected1, update1)
        return descriptor.apply()
    }

    private inner class CAS2Descriptor(
        private val index1: Int,
        private val expected1: E,
        private val update1: E,
        private val index2: Int,
        private val expected2: E,
        private val update2: E
    ) : Descriptor<E> {
        val status = atomic(UNDECIDED)

        override fun help(): Boolean = apply()

        override fun apply(): Boolean {
            val res = tryInstallDescriptor()
            if (res) {
                status.compareAndSet(UNDECIDED, SUCCESS)
            } else {
                status.compareAndSet(UNDECIDED, FAILED)
            }
            updateValues()
            return status.value === SUCCESS
        }

        private fun updateValues() {
            if (status.value == SUCCESS) {
                array[index1].compareAndSet(this, update1)
                array[index2].compareAndSet(this, update2)
            }
            else {
                array[index1].compareAndSet(this, expected1)
                array[index2].compareAndSet(this, expected2)
            }
        }

        private fun tryInstallDescriptor(): Boolean {
            return tryInstallDescriptor(index1, expected1, false) &&
                    tryInstallDescriptor(index2, expected2, true)
        }

        private fun tryInstallDescriptor(index: Int, expected: E, useDCSS: Boolean): Boolean {
            while (true) {
                when (val curState = array[index].value) {
                    this -> return true
                    is Descriptor<*> -> {
                        curState.help()
                    }
                    expected -> {
                        if (useDCSS) {
                            if (DCSSDescriptor(index, expected, this, this, UNDECIDED).apply()) {
                                return true
                            }
                        }
                        else {
                            if (array[index].compareAndSet(expected, this)) {
                                return true
                            }
                        }
                    }
                    else -> return false
                }
            }
        }
        
        override fun getValue(index: Int): E {
            return if (status.value == SUCCESS) getUpdated(index)
            else getExpected(index)
        }
        
        fun getExpected(idx: Int): E {
            return if (index1 == idx) expected1
            else if (index2 == idx) expected2
            else throw IllegalArgumentException("$idx")
        }

        fun getUpdated(idx: Int): E {
            return if (index1 == idx) update1
            else if (index2 == idx) update2
            else throw IllegalArgumentException("$idx")
        }
    }

    private inner class DCSSDescriptor(
        private val index1: Int,
        private val expected1: E,
        private val update: Any,
        private val statusHolder: CAS2Descriptor,
        private val expectedStatus: Status
    ) : Descriptor<E> {
        val status = atomic(UNDECIDED)

        override fun apply(): Boolean = apply(true)
        override fun help(): Boolean = apply(false)

        private fun apply(installDescriptor: Boolean): Boolean {
            if ((!installDescriptor || setDescriptor()) && secondValueEqual()) {
                status.compareAndSet(UNDECIDED, SUCCESS)
            } else {
                status.compareAndSet(UNDECIDED, FAILED)
            }
            updateValue()
            return status.value === SUCCESS
        }

        private fun updateValue() {
            if (status.value === SUCCESS) {
                array[index1].compareAndSet(this, update)
            } else {
                array[index1].compareAndSet(this, expected1)
            }
        }

        fun setDescriptor(): Boolean {
            while (true) {
                when (val v1 = array[index1].value) {
                    this -> return true
                    is Descriptor<*> -> v1.help()
                    expected1 -> {
                        if (array[index1].compareAndSet(expected1, this)) {
                            return true
                        }
                    }
                    else -> return false
                }
            }
        }

        private fun secondValueEqual(): Boolean {
            return statusHolder.status.value === expectedStatus
        }

        override fun getValue(index: Int): E {
            return if (status.value === SUCCESS) {
                if (update is Descriptor<*>) update.getValue(index) as E
                else update as E
            }
            else expected1
        }
    }

    private enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}