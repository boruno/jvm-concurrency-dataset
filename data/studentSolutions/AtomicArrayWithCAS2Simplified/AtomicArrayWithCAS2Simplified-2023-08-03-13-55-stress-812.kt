package day3

import day3.AtomicArrayWithCAS2Simplified.Status.*
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls


// This implementation never stores `null` values.
@Suppress("DuplicatedCode")
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
            value.getValue(index) as E
        } else {
            value as E
        }
    }

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
        if (index1 > index2) {
            return cas2(
                index2, expected2, update2,
                index1, expected1, update1
            )
        }
        val descriptor = CAS2Descriptor(
            index1 = index1, expected1 = expected1, update1 = update1,
            index2 = index2, expected2 = expected2, update2 = update2
        )
        descriptor.apply()
        return descriptor.status.value === SUCCESS
    }

    inner class CAS2Descriptor(
        private val index1: Int,
        private val expected1: E,
        private val update1: E,
        private val index2: Int,
        private val expected2: E,
        private val update2: E
    ) {

        init {
            require(index1 < index2)
        }

        fun getValue(index: Int): E {
            return when (index) {
                index1 -> if (status.value === SUCCESS) update1 else expected1
                index2 -> if (status.value === SUCCESS) update2 else expected2
                else -> error("opa")
            }
        }

        val status = atomic(UNDECIDED)

        fun apply() {
            if (!install1()) {
                status.value = FAILED
                return
            }
            afterInstall1()
        }

        private fun afterInstall1() {
            if (!install2()) {
                status.value = FAILED
                uninstall1()
                return
            }
            afterInstall2()
        }

        private fun afterInstall2() {
            status.value = SUCCESS
            finish1()
            finish2()
        }

        private fun install1(): Boolean {
            val valueBefore1 = getValueToOverride(index1, expected1) ?: return false
            return array[index1].compareAndSet(valueBefore1, this)
        }

        private fun install2(): Boolean {
            val valueBefore2 = getValueToOverride(index2, expected2) ?: return false
            return array[index2].compareAndSet(valueBefore2, this)
        }

        private fun uninstall1(): Boolean {
            return array[index1].compareAndSet(this, expected1)
        }

        private fun finish1(): Boolean {
            return array[index1].compareAndSet(this, update1)
        }

        private fun finish2(): Boolean {
            return array[index2].compareAndSet(this, update2)
        }

        private fun getValueToOverride(index: Int, expected: E): Any? {
            val valueOrDescriptor = array[index].value
            val value = if (valueOrDescriptor is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
                helpAnotherDescriptor(index, valueOrDescriptor)
                if (valueOrDescriptor.status.value === UNDECIDED) {
                    return null
                }
                // descriptor is SUCCESS or FAILED state => treat as a value
                valueOrDescriptor.getValue(index)
            } else {
                valueOrDescriptor
            }
            if (value !== expected) {
                return null
            }
            return valueOrDescriptor
        }

        private fun helpAnotherDescriptor(foundAt: Int, another: AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
            when (foundAt) {
                another.index1 -> another.afterInstall1()
                another.index2 -> another.afterInstall2()
                else -> error("opa")
            }
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}