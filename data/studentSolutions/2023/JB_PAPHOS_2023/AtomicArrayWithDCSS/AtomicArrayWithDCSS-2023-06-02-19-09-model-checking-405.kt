package day3

import kotlinx.atomicfu.*

// This implementation never stores `null` values.
class AtomicArrayWithDCSS<E : Any>(size: Int, initialValue: E) {
    private val array = atomicArrayOfNulls<Any>(size)

    init {
        // Fill array with the initial value.
        for (i in 0 until size) {
            array[i].value = initialValue
        }
    }

    fun get(index: Int): Any {
        // TODO: the cell can store a descriptor
        return array[index].value!!
    }

    fun cas(index: Int, expected: E?, update: E?): Boolean {
        // TODO: the cell can store a descriptor
        while(true) {
            val cur1 = get(index)
            if (cur1 is AtomicArrayWithDCSS<*>.DCSSDescriptor) {
                cur1.applyOperation()
                if (cur1.status.value == Status.FAILED) {
                    continue
                }
            }
            if (get(index) is AtomicArrayWithDCSS<*>.DCSSDescriptor) throw Exception("no int4")
            return array[index].compareAndSet(expected, update)
        }
    }

    fun dcss(
        index1: Int, expected1: E?, update1: E?,
        index2: Int, expected2: E?
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
        // TODO This implementation is not linearizable!
        // TODO Store a DCSS descriptor in array[index1].
        while(true) {
            val cur1 = get(index1)
            val cur2 = get(index2)
            if (cur1 is AtomicArrayWithDCSS<*>.DCSSDescriptor) {
                cur1.applyOperation()
                if (cur1.status.value == Status.FAILED) {
                    continue
                }
                if (get(index1) is AtomicArrayWithDCSS<*>.DCSSDescriptor) throw Exception("no int1")
                if (get(index2) is AtomicArrayWithDCSS<*>.DCSSDescriptor) throw Exception("no int1")
                return false
            }
            if (cur2 is AtomicArrayWithDCSS<*>.DCSSDescriptor) {
                cur2.applyOperation()
                if (cur2.status.value == Status.FAILED) {
                    continue
                }
                if (get(index1) is AtomicArrayWithDCSS<*>.DCSSDescriptor) throw Exception("no int2")
                if (get(index2) is AtomicArrayWithDCSS<*>.DCSSDescriptor) throw Exception("no int2")
                return false
            }
            val dcssDesc = DCSSDescriptor(index1, expected1, index2, update1, expected2)
            dcssDesc.applyOperation()
            if (get(index1) is AtomicArrayWithDCSS<*>.DCSSDescriptor) throw Exception("no int3")
            if (get(index2) is AtomicArrayWithDCSS<*>.DCSSDescriptor) throw Exception("no int3")
            return dcssDesc.status.value == Status.SUCCESS
        }
    }
    private inner class DCSSDescriptor(
        val index1: Int, val valueBefore: E?,
        val index2: Int, val valueAfter: E?, val expected: E?
    ) {
        val status = atomic<Status>(Status.UNDECIDED)

        // TODO: Other threads can call this function
        // TODO: to help completing the operation.
        fun applyOperation() {
            if (status.value == Status.UNDECIDED) {
                val val1 = array[index1].value
                val val2 = array[index2].value
                array[index1].compareAndSet(valueBefore, this)
                if (val1 != this && val1 != valueBefore || val2 != expected) {
                    array[index1].compareAndSet(this, valueBefore)
                    status.compareAndSet(Status.UNDECIDED, Status.FAILED)
                    return
                }
                status.compareAndSet(Status.UNDECIDED, Status.SUCCESS)
                array[index1].compareAndSet(this, valueAfter)
                return
            }
            if (status.value == Status.SUCCESS) {
                array[index1].compareAndSet(this, valueAfter)
                return
            }
            array[index1].compareAndSet(this, valueBefore)
        }
    }
    private enum class Status {
        UNDECIDED, FAILED, SUCCESS
    }
}