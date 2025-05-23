//package mpp.dynamicarray

import kotlinx.atomicfu.*

interface DynamicArray<E> {
    /**
     * Returns the element located in the cell [index],
     * or throws [IllegalArgumentException] if [index]
     * exceeds the [size] of this array.
     */
    fun get(index: Int): E

    /**
     * Puts the specified [element] into the cell [index],
     * or throws [IllegalArgumentException] if [index]
     * exceeds the [size] of this array.
     */
    fun put(index: Int, element: E)

    /**
     * Adds the specified [element] to this array
     * increasing its [size].
     */
    fun pushBack(element: E)

    /**
     * Returns the current size of this array,
     * it increases with [pushBack] invocations.
     */
    val size: Int
}

class DynamicArrayImpl<E> : DynamicArray<E> {
    private val core = atomic(Core<E>(100000))
    private val nextCore = null

    override fun get(index: Int): E {
        return core.value.get(index)
    }

    override fun put(index: Int, element: E) {
        core.value.put(index, element)
    }

    override fun pushBack(element: E) {
        // val locCore = core.value
        // if (locCore.isFull()) {
        //     val currentSize = locCore.size
        //     val next = Core<E>(currentSize * 2)
        //     if (nextCore.compareAndSet(locCore, next)) {
        //
        //     }
        // }
        core.value.pushBack(element)
    }

    override val size: Int get() = core.value.size()
}

private class Core<E>(
    capacity: Int,
) {
    private val array = atomicArrayOfNulls<E>(capacity)
    private val _size = atomic(0)

    fun size() : Int = _size.value

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E {
        require(index < size())
        return array[index].value as E
    }

    fun put(index: Int, element: E) {
        require(index < size())
        array[index].getAndSet(element)
    }

    fun pushBack(element: E) {
        val ind = _size.getAndIncrement()
        put(ind, element)
    }

    fun isFull() : Boolean {
        return size() == array.size
    }
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME
