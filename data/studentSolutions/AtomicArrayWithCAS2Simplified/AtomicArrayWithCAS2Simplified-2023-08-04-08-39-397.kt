//package day3

import AtomicArrayWithCAS2Simplified.Status.*
import kotlinx.atomicfu.*
import java.util.NoSuchElementException


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
        return when(val value = array[index].value) {
            is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor -> value[index] as E
            else -> value as E
        }
    }

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
        val descriptor = newDescriptor(
            index1 = index1, expected1 = expected1, update1 = update1,
            index2 = index2, expected2 = expected2, update2 = update2
        )
        descriptor.apply()
        return descriptor.status.value === SUCCESS
    }

    fun newDescriptor(
        index1: Int,
        expected1: E,
        update1: E,
        index2: Int,
        expected2: E,
        update2: E
    ): CAS2Descriptor =
        if (index1 > index2)
            CAS2Descriptor(index2, expected2, update2, index1, expected1, update1)
        else
            CAS2Descriptor(index1, expected1, update1, index2, expected2, update2)

    inner class CAS2Descriptor(
        private val index1: Int,
        private val expected1: E,
        private val update1: E,
        private val index2: Int,
        private val expected2: E,
        private val update2: E
    ) {
        val status = atomic(UNDECIDED)

        operator fun get(i: Int): E = when (status.value) {
            UNDECIDED, FAILED -> when (i) {
                index1 -> expected1
                index2 -> expected2
                else -> throw NoSuchElementException()
            }
            SUCCESS -> when (i) {
                index1 -> update1
                index2 -> update2
                else -> throw NoSuchElementException()
            }
        }

        fun apply() {
            while(true) {
                if (status.value == UNDECIDED) {
                    if (tryInstallDescriptor())
                        continue
                    updateStatus()
                }
                updateCells()
                return
            }
        }

        // returns true when installation is completed, success or fail
        private fun tryInstallDescriptor(): Boolean =
            when (val result1 = tryInstall(index1, expected1)) {
                is DescriptorInstall.Assist -> {
                    result1.provideHelp()
                    false
                }
                DescriptorInstall.Fail -> true
                DescriptorInstall.Success -> {
                    when (val result2 = tryInstall(index2, expected2)) {
                        is DescriptorInstall.Assist -> {
                            result2.provideHelp()
                            false
                        }
                        else -> true
                    }
                }
            }

        // returns { Success, Fail, Assist<Descriptor> }
        private fun tryInstall(index: Int, expected: E): DescriptorInstall =
            when (val actual = array[index].getAndUpdate { if (it == expected) this else it }) {
                this, expected -> DescriptorInstall.Success
                is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor ->
                    if (actual == this) DescriptorInstall.Success
                    else DescriptorInstall.Assist(actual)
                else -> DescriptorInstall.Fail
            }

        private fun updateStatus() {
            val newStatus = if (array[index1].value == this && array[index2].value == this) SUCCESS else FAILED
            status.compareAndSet(UNDECIDED, newStatus)
        }

        private fun updateCells() {
            when (status.value) {
                SUCCESS -> {
                    array[index1].compareAndSet(this, update1)
                    array[index2].compareAndSet(this, update2)
                }
                FAILED, UNDECIDED -> {
                    array[index1].compareAndSet(this, expected1)
                    array[index2].compareAndSet(this, expected2)
                }
            }
        }

    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }

    sealed class DescriptorInstall {
        object Success : DescriptorInstall()
        object Fail : DescriptorInstall()
        class Assist(private val other: AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) : DescriptorInstall() {
            fun provideHelp() = other.apply()
        }
    }

}