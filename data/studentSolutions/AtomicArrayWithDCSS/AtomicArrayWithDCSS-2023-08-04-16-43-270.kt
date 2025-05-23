//package day3

import kotlinx.atomicfu.*

// This implementation never stores `null` values.
class AtomicArrayWithDCSS<E : Any>(size: Int, initialValue: E) {
    private val array = Array(size) { Cell(initialValue) }

    fun get(index: Int): E? {
        // TODO: the cell can store a descriptor
        return array[index].valueRef.value
    }

    fun cas(index: Int, expected: E?, update: E?): Boolean {
        // TODO: the cell can store a descriptor
        return array[index].valueRef.compareAndSet(expected, update)
    }

    fun dcss(
        index1: Int, expected1: E?, update1: E?,
        index2: Int, expected2: E?
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
        // TODO This implementation is not linearizable!
        // TODO Store a DCSS descriptor in array[index1].
        if (array[index1].valueRef.value != expected1 || array[index2].valueRef.value != expected2) return false
        array[index1].valueRef.value = update1
        return true
    }

    private inner class Cell(initialValue: E?) {
        val valueRef = atomic(initialValue)
    }
}