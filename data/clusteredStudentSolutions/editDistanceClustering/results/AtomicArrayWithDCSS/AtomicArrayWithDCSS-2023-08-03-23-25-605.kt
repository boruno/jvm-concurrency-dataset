package day3

import day3.AtomicArrayWithDCSS.Status.*
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicReferenceArray

// This implementation never stores `null` values.
class AtomicArrayWithDCSS<E : Any>(size: Int, initialValue: E) {
    private val array = AtomicReferenceArray(Array<Any>(size) { initialValue })

    fun get(index: Int): E {
        val valueOrDescriptor = array.get(index)
        val value = if (valueOrDescriptor is AtomicArrayWithDCSS<*>.DCSSDescriptor) {
            valueOrDescriptor.getValue()
        } else {
            valueOrDescriptor
        }
        return value as E
    }

    fun cas(index: Int, expected: E, update: E): Boolean {
        while (true) {
            val witness = array.compareAndExchange(index, expected, update)
            if (witness === expected) {
                return true
            } else if (witness is AtomicArrayWithDCSS<*>.DCSSDescriptor) {
                witness.help()
            } else {
                return false
            }
        }
    }

    fun dcss(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
        val descriptor = DCSSDescriptor(
            index1 = index1, expected1 = expected1, update1 = update1,
            index2 = index2, expected2 = expected2,
        )
        descriptor.apply()
        return descriptor.status.get() === SUCCESS
    }

    private inner class DCSSDescriptor(
        val index1: Int,
        val expected1: E,
        val update1: E,
        val index2: Int,
        val expected2: E,
    ) {

        val status = AtomicReference(UNDECIDED)

        fun getValue(): E {
            return if (status.get() === SUCCESS) update1 else expected1
        }

        fun apply() {
            if (install() && array.get(index2) === expected2) {
                status.compareAndSet(UNDECIDED, SUCCESS)
                array.compareAndSet(index1, this, update1)
            } else {
                status.compareAndSet(UNDECIDED, FAILED)
                array.compareAndSet(index1, this, expected1)
            }
        }

        fun help() {
            if (status.get() == UNDECIDED && array.get(index2) === expected2) {
                status.compareAndSet(UNDECIDED, SUCCESS)
                array.compareAndSet(index1, this, update1)
            } else {
                status.compareAndSet(UNDECIDED, FAILED)
                array.compareAndSet(index1, this, expected1)
            }
        }

        private fun install(): Boolean {
            while (true) {
                val witness1 = array.compareAndExchange(index1, expected1, this)
                return if (witness1 === expected1 || witness1 === this) {
                    true
                } else if (witness1 is AtomicArrayWithDCSS<*>.DCSSDescriptor) {
                    witness1.apply()
                    continue // try again
                } else {
                    false
                }
            }
        }
    }

    private enum class Status {
        UNDECIDED,
        SUCCESS,
        FAILED,
    }
}
