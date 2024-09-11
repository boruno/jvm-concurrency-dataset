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

    fun get(index: Int): E? {
        // TODO: the cell can store a descriptor
        val value = array[index].value
        return when {
            value is AtomicArrayWithCAS2<*>.CAS2Descriptor -> when (value.status.value) {
                Status.SUCCESS -> (if (index == value.index1) value.update1 else value.update2) as E
                else -> (if (index == value.index1) value.expected1 else value.expected2) as E
            }
            value is AtomicArrayWithCAS2<*>.DCSSDescriptor ->
                (if (value.status.value == Status.SUCCESS) {
                    val cas2Descriptor = value.update1 as AtomicArrayWithCAS2<*>.CAS2Descriptor
                    when (cas2Descriptor.status.value) {
                        Status.SUCCESS -> (if (index == cas2Descriptor.index1) cas2Descriptor.update1 else cas2Descriptor.update2) as E
                        else -> (if (index == cas2Descriptor.index1) cas2Descriptor.expected1 else cas2Descriptor.expected2) as E
                    }
                } else {
                    value.expected1
                }) as E
            else -> value as E
        }
    }

    fun cas(index: Int, expected: E, update: E): Boolean {
        // TODO: the cell can store a descriptor
        while (!array[index].compareAndSet(expected, update)) {
            val curState = array[index].value
            if (curState is AtomicArrayWithCAS2<*>.CAS2Descriptor) {
                if (curState.status.value == Status.UNDECIDED) {
                    curState.apply()
                } else {
                    curState.applyValues()
                }
            } else if (curState is AtomicArrayWithCAS2<*>.DCSSDescriptor) {
                curState.apply()
            } else if (curState != expected) {
                return false
            }
        }
        return true
    }

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
        // TODO: this implementation is not linearizable,
        // TODO: Store a CAS2 descriptor in array[index1].
        val descriptor = CAS2Descriptor(
            index1 = index1, expected1 = expected1, update1 = update1,
            index2 = index2, expected2 = expected2, update2 = update2
        )
        descriptor.apply()
        return descriptor.status.value === Status.SUCCESS
    }

    fun dcss(index1: Int, expected1: Any, update1: Any, cas2Descriptor: CAS2Descriptor): Boolean {
        // TODO This implementation is not linearizable!
        // TODO Store a DCSS descriptor in array[index1].
        val descriptor = DCSSDescriptor(index1, expected1, update1, cas2Descriptor)
        while(!array[index1].compareAndSet(expected1, descriptor)) {
            val curState = array[index1].value
            if (curState is AtomicArrayWithCAS2<*>.DCSSDescriptor) {
                curState.apply()
            } else if (curState != expected1) {
                return false
            }
        }
        descriptor.apply()
        return descriptor.status.value == Status.SUCCESS
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

        fun apply() {
            val (cix1, cix2) = if (index1 > index2) index1 to index2 else index2 to index1
            val (e1, e2) = if (cix1 == index1) expected1 to expected2 else expected2 to expected1

            while (!dcss(cix1, e1, this, this)) {
                val curState = array[cix1].value
                if (curState is AtomicArrayWithCAS2<*>.CAS2Descriptor) {
                    if (curState == this) {
                        break
                    }
                    if (curState.status.value == Status.UNDECIDED) {
                        curState.apply()
                    } else {
                        curState.applyValues()
                    }
                } else if (curState != e1 || status.value != Status.UNDECIDED) {
                    status.compareAndSet(Status.UNDECIDED, Status.FAILED)
                    applyValues()
                    return
                }
            }
            while (!dcss(cix2, e2, this, this)) {
                val curState = array[cix2].value
                if (curState is AtomicArrayWithCAS2<*>.CAS2Descriptor) {
                    if (curState == this) {
                        break
                    }
                    if (curState.status.value == Status.UNDECIDED) {
                        curState.apply()
                    } else {
                        curState.applyValues()
                    }
                } else if (curState != e2 || status.value != Status.UNDECIDED) {
                    status.compareAndSet(Status.UNDECIDED, Status.FAILED)
                    applyValues()
                    return
                }
            }
            status.compareAndSet(Status.UNDECIDED, Status.SUCCESS)
            applyValues()
        }

        internal fun applyValues() {
            val status = status.value
            if (status == Status.SUCCESS) {
                array[index1].compareAndSet(this, update1)
                array[index2].compareAndSet(this, update2)
            } else {
                array[index1].compareAndSet(this, expected1)
                array[index2].compareAndSet(this, expected2)
            }
        }
    }

    inner class DCSSDescriptor(val index1: Int,
                               val expected1: Any,
                               val update1: Any,
                               val cas2Descriptor: CAS2Descriptor) {
        val status = atomic(Status.UNDECIDED)

        fun apply() {
            if (cas2Descriptor.status.value != Status.UNDECIDED) {
                status.compareAndSet(Status.UNDECIDED, Status.FAILED)
            } else {
                status.compareAndSet(Status.UNDECIDED, Status.SUCCESS)
            }
            applyValues()
        }

        internal fun applyValues() {
            val status = status.value
            if (status == Status.SUCCESS) {
                array[index1].compareAndSet(this, update1)
            } else {
                array[index1].compareAndSet(this, expected1)
            }
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}
