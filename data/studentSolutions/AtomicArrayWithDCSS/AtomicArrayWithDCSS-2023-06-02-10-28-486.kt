//package day4

import kotlinx.atomicfu.*
import day4.AtomicArrayWithDCSS.Status.*

// This implementation never stores `null` values.
class AtomicArrayWithDCSS<E : Any>(size: Int, initialValue: E) {
    private val array = atomicArrayOfNulls<Any>(size)

    init {
        // Fill array with the initial value.
        for (i in 0 until size) {
            array[i].value = initialValue
        }
    }

    // TODO: the cell can store a descriptor
    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E? = when (val value = array[index].value) {
        is AtomicArrayWithDCSS<*>.DCSSDescriptor -> value.getValueOf(index) as E?
        else -> value as E?
    }

    fun cas(index: Int, expected: Any?, update: Any?): Boolean {
        // TODO: the cell can store a descriptor
        return array[index].compareAndSet(expected, update)
    }

    fun dcss(
        indexA: Int, expected1: E?, update1: E?,
        indexB: Int, expected2: E?
    ): Boolean {
        require(indexA != indexB) { "The indices should be different" }
        // TODO This implementation is not linearizable!
        // TODO Store a DCSS descriptor in array[index1].

        val index1 = if (indexA <= indexB) indexA else indexB
        val index2 = if (indexA <= indexB) indexB else indexA

        val descriptor = DCSSDescriptor(index1, expected1, update1, index2, expected2)
            .also { it.applyOperation() }

        return descriptor.status.value == SUCCESS
    }

    inner class DCSSDescriptor(
        val index1: Int, val expected1: E?, val update1: E?,
        val index2: Int, val expected2: E?,
    ) {
        val status = atomic(UNDECIDED)

        private fun getOldValueOf(index: Int) = when (index) {
            index1 -> expected1
            index2 -> expected2
            else -> error("No such index found in the descriptor: index = $index")
        }

        private fun getNewValueOf(index: Int) = when (index) {
            index1 -> update1
            index2 -> expected2
            else -> error("No such index found in the descriptor: index = $index")
        }

        fun getValueOf(index: Int) = when (status.value) {
            UNDECIDED, FAILED -> getOldValueOf(index)
            SUCCESS -> getNewValueOf(index)
        }

        private fun canOccupyCell(index: Int, expected: E?): Boolean {
            cas(index1, expected1, this)

            when (val value = array[index1].value) {
                this -> return true
                is AtomicArrayWithDCSS<*>.DCSSDescriptor -> value.applyOperation()
                expected1 -> {} // really?, fuck you
                else -> status.compareAndSet(UNDECIDED, FAILED)
            }

            return false
        }

        fun applyOperation() {
            while (status.value != UNDECIDED) {
                if (canOccupyCell(index1, expected1)) {
                    break
                }
            }

            while (status.value != UNDECIDED) {
                if (canOccupyCell(index2, expected2)) {
                    break
                }
            }

            when (status.value) {
                SUCCESS -> cas(index1, this, update1)
                FAILED -> cas(index1, this, expected1)
                else -> error("Should not have come this far")
            }

        }
    }

    enum class Status {
        UNDECIDED, FAILED, SUCCESS
    }
}