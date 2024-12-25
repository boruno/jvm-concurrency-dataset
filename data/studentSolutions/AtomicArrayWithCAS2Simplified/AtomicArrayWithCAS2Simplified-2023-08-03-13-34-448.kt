//package day3

import day3.AtomicArrayWithCAS2Simplified.Status.*
import kotlinx.atomicfu.*


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
        while(true) {
            val cur = array[index].value!!
            if (cur is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
                cur.apply()
                continue
            }
            return cur as E
        }
        // TODO: the cell can store CAS2Descriptor
    }

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
        val descriptor = CAS2Descriptor(
            index1 = index1, expected1 = expected1, update1 = update1,
            index2 = index2, expected2 = expected2, update2 = update2
        )
        descriptor.apply()
        return descriptor.status.value === SUCCESS
    }

    inner class CAS2Descriptor(
         val index1: Int,
         val expected1: E,
         val update1: E,
         val index2: Int,
         val expected2: E,
         val update2: E
    ) {
        val status = atomic(UNDECIDED)


        fun apply() {

            val cell1 = array[index1].value
            val cell2 = array[index2].value

            if (cell1 is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor && cell1.status.value == UNDECIDED){
                if (array[cell1.index2].compareAndSet(cell1.expected2, this)) {
                    array[cell1.index1].compareAndSet(cell1, cell1.update1)
                    array[cell1.index2].compareAndSet(this, cell1.update2)
                    cell1.status.value = SUCCESS
                }
            }
            if (cell2 is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor && cell2.status.value == UNDECIDED){
                if(array[cell2.index1].compareAndSet(cell2.expected1, this)) {
                    array[cell2.index1].compareAndSet(this, cell2.update1)
                    array[cell2.index2].compareAndSet(cell2, cell2.update2)
                    cell2.status.value = SUCCESS
                }
            }

            if (!array[index1].compareAndSet(expected1, this)) {
                status.value = FAILED
                return
            }
            if (!array[index2].compareAndSet(expected2, this)) {
                array[index1].compareAndSet(this, expected1)
                status.value = FAILED
                return
            }

            status.value = SUCCESS

            array[index1].compareAndSet(this, update1)
            array[index2].compareAndSet(this, update2)
        }

//        fun apply() {
//
//            val val1 = array[index1].value!!
//            val val2 = array[index2].value!!
//
//            if (index1 > index2){
//                if (val1 is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor && val1.status.value == UNDECIDED) {
//                    if (!array[index2].compareAndSet(val1.expected2, this)) { // revert first install
//                        array[index1].compareAndSet(val1, val1.expected1)
//                        status.value = FAILED
//                        return
//                    }
//                    status.value = SUCCESS
//
//                    array[index1].compareAndSet(this, val1.update1)
//                    array[index2].compareAndSet(this, val1.update2)
//                }
//                if (val2 is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor && val2.status.value == UNDECIDED) {
//                    if (!array[index1].compareAndSet(val2.expected1, this)) { // revert first install
//                        array[index2].compareAndSet(val2, val2.expected2)
//                        status.value = FAILED
//                        return
//                    }
//                    status.value = SUCCESS
//
//                    array[index1].compareAndSet(this, val2.update1)
//                    array[index2].compareAndSet(this, val2.update2)
//                }
//            } else {
//                if (val2 is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor && val2.status.value == UNDECIDED) {
//                    if (!array[index1].compareAndSet(val2.expected1, this)) { // revert first install
//                        array[index2].compareAndSet(val2, val2.expected2)
//                        status.value = FAILED
//                        return
//                    }
//                    status.value = SUCCESS
//
//                    array[index1].compareAndSet(this, val2.update1)
//                    array[index2].compareAndSet(this, val2.update2)
//                }
//
//                if (val1 is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor && val1.status.value == UNDECIDED) {
//                    if (!array[index2].compareAndSet(val1.expected2, this)) { // revert first install
//                        array[index1].compareAndSet(val1, val1.expected1)
//                        status.value = FAILED
//                        return
//                    }
//                    status.value = SUCCESS
//
//                    array[index1].compareAndSet(this, val1.update1)
//                    array[index2].compareAndSet(this, val1.update2)
//                }
//            }
//
//
////            this.apply()
////            if (val2 is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor && val2.status.value == UNDECIDED) {
////                return val2.apply()
////            }
//
//
//            // TODO: Install the descriptor, update the status, and update the cells;
//            // TODO: create functions for each of these three phases.
//            if (!array[index1].compareAndSet(expected1, this)) {
//                status.value = FAILED
//                return
//            }
//            if (!array[index2].compareAndSet(expected2, this)) { // revert first install
//                array[index1].compareAndSet(this, expected1)
//                status.value = FAILED
//                return
//            }
//
//            status.value = SUCCESS
//
//            array[index1].compareAndSet(this, update1)
//            array[index1].compareAndSet(this, update2)
//        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}
