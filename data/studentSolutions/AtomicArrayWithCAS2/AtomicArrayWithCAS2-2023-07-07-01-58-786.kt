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

    fun getFromCAS2Descriptor(value: AtomicArrayWithCAS2<*>.CAS2Descriptor, index: Int): E? {
        val status = value.status.value
        if (status == Status.UNDECIDED || status == Status.FAILED) return if (value.index1 == index) value.expected1 as? E else value.expected2 as? E

//        value.apply()

        return if (value.index1 == index) value.update1 as? E else value.update2 as? E
    }

    fun get(index: Int): E? {
        // TODO: the cell can store CAS2Descriptor
        val value = array[index].value
        if (value is AtomicArrayWithCAS2<*>.CAS2Descriptor) {
            return getFromCAS2Descriptor(value, index)
        }
        if (value is AtomicArrayWithCAS2<*>.CAS2Descriptor.DSSDescriptor) {
            val status = value.dssStatus.value
            if (status == Status.UNDECIDED || status == Status.FAILED) {
                if (value.expected !is AtomicArrayWithCAS2<*>.CAS2Descriptor)
                    return value.expected as? E
                return getFromCAS2Descriptor(value.expected, index) as? E
            }
//            value.apply()

            if (value.update !is AtomicArrayWithCAS2<*>.CAS2Descriptor)
                return value.update as? E
            return getFromCAS2Descriptor(value.update, index) as? E
        }

        return value as? E
    }

    fun cas(index: Int, expected: E?, update: E?): Boolean {
        while (true) {
            val value = array[index].value
            if (value is AtomicArrayWithCAS2<*>.CAS2Descriptor) {
                value.apply()
                continue
            }

            if (value is AtomicArrayWithCAS2<*>.CAS2Descriptor.DSSDescriptor) {
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
        return caS2Descriptor.apply() == Status.SUCCESS
    }

    inner class CAS2Descriptor(
        val index1: Int, val expected1: E?, val update1: E?, val index2: Int, val expected2: E?, val update2: E?
    ) {
        val status = atomic<Any>(Status.UNDECIDED)

        private fun tss(index1: Int, expected1: Any?, update1: Any?, setStatus: Status): Boolean {
            val dssDescriptor = DSSDescriptor(index1, expected1, update1, setStatus)
            return dssDescriptor.apply() == Status.SUCCESS
        }

        inner class DSSDescriptor(val index: Int, val expected: Any?, val update: Any?, val expected2: Status) {
            val dssStatus = atomic<Status>(Status.UNDECIDED)
            fun apply() : Status{
                while (true) {
                    val localStatus = dssStatus.value
                    if (localStatus == Status.SUCCESS) {
                        array[index].compareAndSet(this, update)
                        status.compareAndSet(this, expected2)
                        return Status.SUCCESS
                    }

                    if (localStatus == Status.FAILED) {
                        array[index].compareAndSet(this, expected)
                        status.compareAndSet(this, expected2)
                        return Status.FAILED
                    }

                    val fst = array[index].value
                    val snd = status.value
                    if ((fst !is AtomicArrayWithCAS2<*>.CAS2Descriptor.DSSDescriptor && fst != expected) ||
                        (snd !is AtomicArrayWithCAS2<*>.CAS2Descriptor.DSSDescriptor && snd != expected2)
                    ) {
                        dssStatus.compareAndSet(Status.UNDECIDED, Status.FAILED)
                        continue
                    }

                    if (!array[index].compareAndSet(expected, this)) {
                        val value = array[index].value
                        if (value != this) {
                            if (value is AtomicArrayWithCAS2<*>.CAS2Descriptor.DSSDescriptor)
                                value.apply()
                            continue
                        }
                    }

                    if (!status.compareAndSet(expected2, this)) {
                        val value = status.value
                        if (value != this) {
                            if (value is AtomicArrayWithCAS2<*>.CAS2Descriptor.DSSDescriptor) {
                                value.apply()
                                continue
                            }

                            dssStatus.compareAndSet(Status.UNDECIDED, Status.FAILED)
                            continue
                        }
                    }

                    dssStatus.compareAndSet(Status.UNDECIDED, Status.SUCCESS)
                }
            }
        }

        fun apply() : Status{
            while (true) {
                val localStatus = status.value

                if (localStatus is AtomicArrayWithCAS2<*>.CAS2Descriptor.DSSDescriptor) {
                    localStatus.apply()
                    continue
                }

                if (localStatus == Status.SUCCESS) {
                    tss(index1, this, update1, Status.SUCCESS)
                    tss(index2, this, update2, Status.SUCCESS)
                    return Status.SUCCESS
                }

                if (localStatus == Status.FAILED) {
                    tss(index1, this, expected1, Status.FAILED)
                    tss(index2, this, expected2, Status.FAILED)
                    return Status.FAILED
                }

                val fst = array[index1].value
                val snd = array[index2].value

                if ((fst !is AtomicArrayWithCAS2<*>.CAS2Descriptor && fst !is AtomicArrayWithCAS2<*>.CAS2Descriptor.DSSDescriptor && fst != expected1) ||
                    (snd !is AtomicArrayWithCAS2<*>.CAS2Descriptor && snd !is AtomicArrayWithCAS2<*>.CAS2Descriptor.DSSDescriptor && snd != expected2)
                ) {
                    status.compareAndSet(Status.UNDECIDED, Status.FAILED)
                    continue
                }

                if (!tss(index1, expected1, this, Status.UNDECIDED)) {
                    val v = array[index1].value
                    if (this != v) {
                        if (v is AtomicArrayWithCAS2<*>.CAS2Descriptor) {
                            v.apply()
                        }
                        continue
                    }
                }

                if (!tss(index2, expected2, this, Status.UNDECIDED)) {
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