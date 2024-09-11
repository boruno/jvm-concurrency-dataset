@file:Suppress("DuplicatedCode")

package day3

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

            return value as? E
        }
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
        caS2Descriptor.apply()
        return caS2Descriptor.status.value == Status.SUCCESS
    }

    inner class CAS2Descriptor(
        val index1: Int, val expected1: E?, val update1: E?, val index2: Int, val expected2: E?, val update2: E?
    ) {
        val status = atomic<Any>(Status.UNDECIDED)

        private fun tss(index1: Int, expected1: Any?, update1: Any?, setStatus: Status): Boolean {
            val dssDescriptor = DSSDescriptor(index1, expected1, update1, setStatus)
            dssDescriptor.apply()
            return dssDescriptor.dssStatus.value == Status.SUCCESS
        }

        inner class DSSDescriptor(
            val dssIndex: Int,
            val dssExpected: Any?,
            val dssUpdate: Any?,
            val dssExpected2: Status
        ) {
            val dssStatus = atomic(Status.UNDECIDED)
            fun apply() {
                while (true) {
                    val localDssStatus = dssStatus.value
                    if (localDssStatus == Status.SUCCESS) {
                        array[dssIndex].compareAndSet(this, dssUpdate)
                        return
                    }

                    if (localDssStatus == Status.FAILED) {
                        array[dssIndex].compareAndSet(this, dssExpected)
                        return
                    }

                    if (!array[dssIndex].compareAndSet(dssExpected, this)) {
                        val value = array[dssIndex].value
                        if (this != value) {
                            if (value is AtomicArrayWithCAS2<*>.CAS2Descriptor.DSSDescriptor) {
                                value.apply()
                                continue
                            }

                            dssStatus.compareAndSet(Status.UNDECIDED, Status.FAILED)
                            continue
                        }
                    }

                    if (!status.compareAndSet(dssExpected2, dssExpected2)) {
                        dssStatus.compareAndSet(Status.UNDECIDED, Status.FAILED)
                        continue
                    }

                    dssStatus.compareAndSet(Status.UNDECIDED, Status.SUCCESS)
                }
            }
        }

        fun apply() {
            while (true) {
                val localStatus = status.value

                if (localStatus == Status.SUCCESS) {
                    tss(index1, this, update1, Status.SUCCESS)
                    tss(index2, this, update2, Status.SUCCESS)
                    return
                }

                if (localStatus == Status.FAILED) {
                    tss(index1, this, expected1, Status.FAILED)
                    tss(index2, this, expected2, Status.FAILED)
                    return
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

                        if (v is AtomicArrayWithCAS2<*>.CAS2Descriptor.DSSDescriptor) {
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

                        if (v is AtomicArrayWithCAS2<*>.CAS2Descriptor.DSSDescriptor) {
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