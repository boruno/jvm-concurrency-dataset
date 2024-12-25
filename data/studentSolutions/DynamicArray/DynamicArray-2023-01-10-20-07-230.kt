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

    val capacity: Int
}

class DynamicArrayImpl<E> : DynamicArray<E> {
    private val core = atomic(Core<E>(INITIAL_CAPACITY))

    override fun get(index: Int): E = core.value.get(index)

    override fun put(index: Int, element: E) = core.value.put(index, element)

    override fun pushBack(element: E) {
        print("Push: ")
        print(element)
        print(" New capacity: ")
        println(capacity * 2)

        val oldCore = core.value
        val newCore = Core<E>(oldCore.maxSize * 2)
        for (i in 0 until oldCore.size) {
            newCore.put(i, oldCore.get(i))
        }
        newCore.put(oldCore.size, element)
        core.compareAndSet(oldCore, newCore)
//        core.value = newCore
//        if (tail < size - 1) {
//            put(tail + 1, element)
//        } else {
//            val oldCore = core.value
//            val newCore = Core<E>(oldCore.size * 2)
//            for (i in 0 until oldCore.size) {
//                newCore.put(i, oldCore.get(i))
//            }
//            newCore.put(oldCore.tail + 1, element)
//            core.compareAndSet(oldCore, newCore)
//        }
    }

    override val size: Int get() = core.value.size

    override val capacity: Int get() = core.value.maxSize
}

private class Core<E>(
    capacity: Int,
) {
    private val array = atomicArrayOfNulls<E>(capacity)
    private val _size = atomic(0)

    val size: Int = _size.value
    val maxSize: Int = capacity

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E {
        print("Get: ")
        println(index)

        require(index < size)
        return array[index].value as E
    }

    @Suppress("UNCHECKED_CAST")
    fun put(index: Int, element: E) {
        print("Put: ")
        print(element)
        print(" in ")
        println(index)

        require(index < array.size)
        val prev = array[index].value
        if (array[index].compareAndSet(prev, element)) {
            if (index >= size) {
                val oldSize = size
                _size.compareAndSet(oldSize, index + 1)
            }
        }
    }
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME