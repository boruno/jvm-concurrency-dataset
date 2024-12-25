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
        val v:Any? = array[index].value
        return if (v is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) v.va(index) as E else v as E
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
        private val index1: Int,
        private val expected1: E,
        private val update1: E,
        private val index2: Int,
        private val expected2: E,
        private val update2: E
    ) {
        val status: AtomicRef<Status> = atomic(UNDECIDED)

        fun apply() {
            if (!install()) {
                status.compareAndSet(UNDECIDED, FAILED)
                return
            }
            if (!status.compareAndSet(UNDECIDED, SUCCESS)) {
                return
            }
            updateCellsBack()
        }

        private fun updateCellsBack() {
            array[index1].compareAndSet(this, update1)
            array[index2].compareAndSet(this, update2)
        }

        private fun install(): Boolean {
            var i1:Int=index1
            var i2:Int=index2
            var e1:E=expected1
            var e2:E=expected2
            if (index1>index2) {
              i1 = index2
              i2 = index1
              e1 = expected2
              e2=expected1
            }
            if (!inst(i1, e1)
                ||
                !inst(i2, e2)
            ) {
                array[index1].compareAndSet(this, expected1)
                array[index2].compareAndSet(this, expected2)
                return false
            }
            return true
        }

        private fun inst(index: Int, expected: E): Boolean {
            while (true) {
                if (array[index].compareAndSet(expected, this)) {
                    return true
                }
                val v = array[index].value
                if (v !is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
                    return false
                }
                if (v == this) {
                    return true
                }
//                if (v.status.value == UNDECIDED && (if(index==v.index1)v.update1 else v.update2) == expected) {
//                    continue
//                }
                // help
                while (array[index].value is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {}
//                v.apply()
            }
        }

        fun va(index:Int):E = if (index == index1) if (status.value == SUCCESS) update1 else expected1
        else if (status.value == SUCCESS) update2 else expected2

        override fun toString(): String {
            return "CASD($index1 $expected1 $update1, $index2 $expected2 $update2)"
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}