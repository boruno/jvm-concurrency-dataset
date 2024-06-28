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
    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E?{
        // TODO: the cell can store a descriptor
        val k = array[index].value
        if (k is AtomicArrayWithDCSS<*>.DescriptorDCSS) {
            if (k.status.value === Status.SUCCESS)
                return k.update1 as E?
            else
                return k.expected1 as E?
        }
        return k as E?
    }

    fun cas(index: Int, expected: E?, update: E?): Boolean {
        // TODO: the cell can store a descriptor
//        val k = array[index].value
//        if (k is AtomicArrayWithDCSS<*>.DescriptorDCSS) {
//            k.runningDisc()
//        }
        return array[index].compareAndSet(expected, update)
    }

    private inner class DescriptorDCSS(val index1: Int, val expected1: E?, val update1: E?,
                               val index2: Int, val expected2: E?)
    {
        val status = atomic(Status.UNDECIDED)

        fun runningDisc(){
            val b = get(index2)
            if (b == expected2)
                status.compareAndSet(Status.UNDECIDED, Status.SUCCESS)
            else
                status.compareAndSet(Status.UNDECIDED, Status.FAILED)
            if (status.value === Status.SUCCESS)
                array[index1].compareAndSet(this, update1)
            if (status.value === Status.FAILED)
                array[index1].compareAndSet(this, expected1)
        }
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
        // var desc = DescriptorDCSS(index1, expected1, update1, index2, expected2)
        val desc = DescriptorDCSS(index1, expected1, update1, index2, expected2)
        while (true){
            if (array[index1].compareAndSet(expected1, desc)){
                desc.runningDisc()
                break
            }
            else{
                if (array[index1].value is AtomicArrayWithDCSS<*>.DescriptorDCSS){
                    val m_desc = array[index1].value
                    if (m_desc is AtomicArrayWithDCSS<*>.DescriptorDCSS){
                        m_desc.runningDisc()
                    }
                    continue
                }
                return false
            }
        }


        if (desc.status.value == Status.SUCCESS)
            return true
        return false
    }
}