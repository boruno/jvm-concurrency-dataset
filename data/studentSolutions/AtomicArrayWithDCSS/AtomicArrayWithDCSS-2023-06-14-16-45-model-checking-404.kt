package day4

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

    fun get(index: Int): Any? {
        // TODO: the cell can store a descriptor
        return array[index].value
    }

    fun cas(index: Int, expected: Any?, update: Any?): Boolean {
        // TODO: the cell can store a descriptor
        return array[index].compareAndSet(expected, update)
    }

    private inner class DescriptorDCSS(val index1: Int, val expected1: E?, val update1: E?,
                               val index2: Int, val expected2: E?)
    {
        final var status = Status.UNDECIDED

    }
    private enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }

    fun dcss(
        index1: Int, expected1: E?, update1: E?,
        index2: Int, expected2: E?
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
        // TODO This implementation is not linearizable!
        // TODO Store a DCSS descriptor in array[index1].
        var desc = DescriptorDCSS(index1, expected1, update1, index2, expected2)
        // var flag = false

//        while (desc.status == Status.UNDECIDED){
//            if (array[index1].compareAndSet(expected1, desc)){
//                if (array[index2].compareAndSet(expected2, expected2)){
//                    desc.status = Status.SUCCESS
//                    array[index1].compareAndSet(desc, update1)
//                    return true
//                }
//                else{
//                    if (array[index2].value is AtomicArrayWithDCSS<*>.DescriptorDCSS) {
//                        continue // helping
//                    }
//                    desc.status = Status.FAILED
//                    array[index1].compareAndSet(atomic(DescriptorDCSS(index1, expected1, update1, index2, expected2)), desc) // ???
//                    array[index1].compareAndSet(desc, expected1)
//                    return false
//                }
//
//            }
//            else {
//                if (array[index1].value is AtomicArrayWithDCSS<*>.DescriptorDCSS){
//                    // var m_desc : DescriptorDCSS = array[index1] as AtomicArrayWithDCSS<E>.DescriptorDCSS
//                    continue
//                    // helping
//                }
//                desc.status = Status.FAILED
//                return false
//            }
//
//        }
//        if (array[index1].value != expected1 || array[index2].value != expected2) return false
//        array[index1].value = update1
        return true
    }



}