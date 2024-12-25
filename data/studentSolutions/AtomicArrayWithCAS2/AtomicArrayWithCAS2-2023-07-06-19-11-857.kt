@file:Suppress("DuplicatedCode")

//package day3

import day3.AtomicArrayWithCAS2.Status.*
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
        return if (v is AtomicArrayWithCAS2<*>.Descriptor) {
            v.getValue(index) as E
        } else {
            v as E
        }
    }

    fun cas(index: Int, expected: E, update: E): Boolean {
        while (true) {
            when (val v = array[index].value) {
                is AtomicArrayWithCAS2<*>.Descriptor -> v.help()
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
        descriptor.apply()
        return descriptor.status.value == SUCCESS
    }
    
    private abstract inner class Descriptor {
        abstract fun getValue(index: Int): E
        abstract fun apply()
        abstract fun help()
    }

    private inner class CAS2Descriptor(
        private val index1: Int,
        private val expected1: E,
        private val update1: E,
        private val index2: Int,
        private val expected2: E,
        private val update2: E
    ) : Descriptor() {
        val status = atomic(UNDECIDED)

        override fun apply() = help()

        override fun help() {
            if (status.value === UNDECIDED) {
                val res = tryInstallDescriptor()
                if (res) {
                    status.compareAndSet(UNDECIDED, SUCCESS)
                } else {
                    status.compareAndSet(UNDECIDED, FAILED)
                }
            }
            updateValues()
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
            return tryInstallDescriptor(index1, expected1) &&
                    tryInstallDescriptor(index2, expected2)
        }

        private fun tryInstallDescriptor(index: Int, expected: E): Boolean {
            while (true) {
                when (val curState = array[index].value) {
                    this -> return true
                    is AtomicArrayWithCAS2<*>.Descriptor -> {
                        curState.help()
                    }
                    expected -> {
                        if (array[index].compareAndSet(expected, this)) {
                            return true
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
        private val update: E,
        private val index2: Int,
        private val expected2: E
    ) : Descriptor() {
        val status = atomic(UNDECIDED)

        override fun apply() = run(false)

        override fun help() = run(true)

        private fun run(isHelp: Boolean) {
            if ((isHelp || setDescriptor()) && secondValueEqual()) {
                status.compareAndSet(UNDECIDED, SUCCESS)
            } else {
                status.compareAndSet(UNDECIDED, FAILED)
            }
            updateValue()
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
                    is AtomicArrayWithCAS2<*>.Descriptor -> v1.help()
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
            return when (val v2 = array[index2].value) {
                is AtomicArrayWithCAS2<*>.Descriptor -> {
                    v2.getValue(index2) == expected2
                }
                expected2 -> true
                else -> false
            }
        }

        override fun getValue(index: Int): E = getValue()

        private fun getValue(): E {
            return if (status.value === SUCCESS) update
            else expected1
        }
    }

    private enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}