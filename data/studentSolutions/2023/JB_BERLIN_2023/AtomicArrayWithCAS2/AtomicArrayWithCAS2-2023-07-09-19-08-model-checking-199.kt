@file:Suppress("DuplicatedCode")

package day3

import day3.AtomicArrayWithCAS2.Status.*
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import kotlin.math.min

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
        val curVal = array[index].value
        val descriptor = curVal as? Descriptor<E?> ?: return curVal as E?
        return descriptor.curVal(index)
    }

    fun cas(index: Int, expected: E?, update: E?): Boolean {
        val desc = CASDescriptor(index, expected, update)
        desc.installAndApply()
        return desc.result
    }
    fun dcss(firstIndex: Int, expected: E?, caS2Descriptor: CAS2Descriptor, status: Status): Boolean {
        val desc = DCSSDescriptor(firstIndex, expected, caS2Descriptor, status)
        desc.installAndApply()
        return desc.result
    }

    fun cas2(
        index1: Int, expected1: E?, update1: E?,
        index2: Int, expected2: E?, update2: E?
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
        // TODO: this implementation is not linearizable,
        // TODO: Store a CAS2 descriptor in array[index1].
        require(index1 != index2) { "The indices should be different" }
        val descriptor = CAS2Descriptor(index1, expected1, update1, index2, expected2, update2)
        descriptor.apply()
        return descriptor.result
    }
    inner class CAS2Descriptor(
        val index1: Int,
        val expected1: E?,
        val update1: E?,
        val index2: Int,
        val expected2: E?,
        val update2: E?
    ): Descriptor<E> {
        override fun installAndApply() {
            TODO("Not yet implemented")
        }

        override fun apply() {
            val from1 = min(index1, index2) == index1
            val firstIndex = if (from1) index1 else index2
            val firstExpected = if (from1) expected1 else expected2
            val secondIndex = if (from1) index2 else index1
            val secondExpected = if (from1) expected2 else expected1

            if (status.value == UNDECIDED) {
                val success = tryInstall(firstIndex, firstExpected) && tryInstall(secondIndex, secondExpected)
                if (success) status.compareAndSet(
                    UNDECIDED,
                    SUCCESS
                ) else status.compareAndSet(
                    UNDECIDED,
                    FAILED
                )
            }
            updateValues()
        }
        private fun tryInstall(firstIndex: Int, expected: E?): Boolean {
            while (true) {
                when (val curState = array[firstIndex].value) {
                    this -> return true
                    is Descriptor<*> -> curState.apply()
                    expected -> {
                        val wasExpected = dcss(firstIndex, expected, this, UNDECIDED)
                        if (wasExpected) return true else continue
                    }
                    else -> return false
                }
            }
        }
        private fun updateValues() {
            if (status.value == SUCCESS) {
                array[index1].compareAndSet(this, update1)
                array[index2].compareAndSet(this, update2)
            } else if (status.value == FAILED) {
                array[index1].compareAndSet(this, expected1)
                array[index2].compareAndSet(this, expected2)
            }
        }

        override val result: Boolean
            get() = status.value === SUCCESS

        val status = atomic(UNDECIDED)
        override fun curVal(index: Int): E? = when (index) {
            index1 -> if (result) update1 else expected1
            index2 -> if (result) update2 else expected2
            else -> throw IllegalArgumentException("Unsupported index $index")
        }
    }
    interface Descriptor<E> {
        fun installAndApply()
        fun apply()
        val result: Boolean
        fun curVal(index: Int):E?
    }
    inner class DCSSDescriptor(val index: Int, val expected: E?, val update: CAS2Descriptor, val expectedStatus: Status) : Descriptor<E> {
        val status = atomic(UNDECIDED)
        override fun curVal(index: Int) = if (result) update.curVal(index) else expected

        override fun installAndApply() {
            while (true) {
                if (array[index].compareAndSet(expected, this)) {
                    apply()
                    return
                }
                when (val curVal = array[index].value) {
                    is AtomicArrayWithDCSS.Descriptor -> curVal.invoke()
                    else -> {
                        if (array[index].compareAndSet(expected, this)) apply()
                        return
                    }
                }
            }
        }


        override fun apply() {
            if (expectedStatus == update.status.value) {
                status.compareAndSet(UNDECIDED, SUCCESS)
                array[index].compareAndSet(this, update)
            } else {
                status.compareAndSet(UNDECIDED, FAILED)
                array[index].compareAndSet(this, expected)
            }
        }

        override val result: Boolean
            get() = status.value === SUCCESS


    }
    inner class CASDescriptor(val index: Int, val expected1: E?, val update: E?) : Descriptor<E> {
        private val status = atomic(UNDECIDED)
        override fun curVal(index: Int) = if (result) update else expected1

        override fun installAndApply() {
            while (true) {
                if (array[index].compareAndSet(expected1, this)) {
                    apply()
                    return
                }
                when (val curVal = array[index].value) {
                    is AtomicArrayWithDCSS.Descriptor -> curVal.invoke()
                    else -> {
                        if (array[index].compareAndSet(expected1, this)) apply()
                        return
                    }
                }
            }
        }

        override fun apply() {
            status.compareAndSet(UNDECIDED, SUCCESS)
            array[index].compareAndSet(this, update)
        }

        override val result: Boolean
            get() = status.value === SUCCESS

    }
    enum class Status{
        UNDECIDED, FAILED, SUCCESS
    }
}