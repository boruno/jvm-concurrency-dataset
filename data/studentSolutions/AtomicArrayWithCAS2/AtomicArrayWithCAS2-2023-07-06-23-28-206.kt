@file:Suppress("DuplicatedCode")

//package day3

import kotlinx.atomicfu.*

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
        // TODO: the cell can store CAS2Descriptor
        val value = array[index].value
        if (value !is AtomicArrayWithCAS2<*>.CAS2Descriptor) return value as? E

        val status = value.status.value
        if (status == Status.UNDECIDED || status == Status.FAILED) return if (value.index1 == index) value.expected1 as? E else value.expected2 as? E

        value.apply()

        return if (value.index1 == index) value.update1 as? E else value.update2 as? E
    }

    fun cas(index: Int, expected: E?, update: E?): Boolean {
        while (true) {
            val value = array[index].value
            if (value is AtomicArrayWithCAS2<*>.CAS2Descriptor) {
                value.apply()
                continue
            }

            if (value != expected) return false
            if (array[index].compareAndSet(expected, update))
                return true
        }
    }

    fun cas2(
        index1: Int, expected1: E?, update1: E?,
        index2: Int, expected2: E?, update2: E?
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
        val caS2Descriptor = if (index1 < index2) CAS2Descriptor(
            index1,
            expected1,
            update1,
            index2,
            expected2,
            update2
        ) else CAS2Descriptor(index2, expected2, update2, index1, expected1, update1)
        caS2Descriptor.apply()
        return caS2Descriptor.status.value == Status.SUCCESS
    }

    inner class CAS2Descriptor(
        val index1: Int, val expected1: E?, val update1: E?, val index2: Int, val expected2: E?, val update2: E?
    ) {
        val status = atomic<Any>(Status.UNDECIDED)

        private fun tss(index1: Int, expected1: E?, update1: E?): Boolean {
            val dssDescriptor = DSSDescriptor(index1, expected1, update1)
            dssDescriptor.apply()
            return dssDescriptor.status2.value == Status.SUCCESS
        }

        inner class DSSDescriptor(val index1: Int, val expected1: E?, val update1: E?) {
            val status2 = atomic<Status>(Status.UNDECIDED)
            fun apply() {
                while (true) {
                    val localStatus = status2.value
                    if (localStatus == Status.SUCCESS) {
                        array[index1].compareAndSet(this, update1)
                        status.compareAndSet(this, Status.UNDECIDED)
                        return
                    }

                    if (localStatus == Status.FAILED) {
                        array[index1].compareAndSet(this, expected1)
                        return
                    }

                    if (!array[index1].compareAndSet(expected1, this)) {
                        val value = array[index1].value
                        if (value != this) {
                            if (value is AtomicArrayWithCAS2<*>.CAS2Descriptor.DSSDescriptor)
                                value.apply()
                            continue
                        }
                    }

                    if (!status.compareAndSet(Status.UNDECIDED, this)) {
                        val value = status.value
                        if (value != this) {
                            if (value is AtomicArrayWithCAS2<*>.CAS2Descriptor.DSSDescriptor) {
                                value.apply()
                                continue
                            }

                            status2.compareAndSet(Status.UNDECIDED, Status.FAILED)
                            continue
                        }
                    }

                    status2.compareAndSet(Status.UNDECIDED, Status.SUCCESS)
                }
            }
        }

        fun apply() {
            while (true) {
                val localStatus = status.value

                if (localStatus == Status.SUCCESS) {
                    array[index1].compareAndSet(this, update1)
                    array[index2].compareAndSet(this, update2)
                    return
                }

                if (localStatus == Status.FAILED) {
                    array[index1].compareAndSet(this, expected1)
                    array[index2].compareAndSet(this, expected2)
                    return
                }

                val fst = array[index1].value
                val snd = array[index2].value

                if ((fst !is AtomicArrayWithCAS2<*>.CAS2Descriptor && fst != expected1) ||
                    (snd !is AtomicArrayWithCAS2<*>.CAS2Descriptor && snd != expected2)
                ) {
                    status.compareAndSet(Status.UNDECIDED, Status.FAILED)
                    continue
                }

                if (!tss(index1, expected1, update1)) {
                    val v = array[index1].value
                    if (this != v) {
                        if (v is AtomicArrayWithCAS2<*>.CAS2Descriptor) {
                            v.apply()
                        }
                        continue
                    }
                }

                if (!tss(index2, expected2, update2)) {
                    val v = array[index2].value
                    if (this != v) {
                        if (v is AtomicArrayWithCAS2<*>.CAS2Descriptor) {
                            v.apply()
                        }
                        continue
                    }
                }

                status.compareAndSet(Status.UNDECIDED, Status.SUCCESS)
            }
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}