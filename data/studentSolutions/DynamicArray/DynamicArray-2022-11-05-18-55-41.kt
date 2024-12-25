//package mpp.dynamicarray

import kotlinx.atomicfu.*

/**
 * @author Цветков Николай
 */

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
    private val core = atomic(Core<E>(INITIAL_CAPACITY))

    override fun get(index: Int): E = core.value.get(index)

    override fun put(index: Int, element: E) {
        core.value.put(index, element)
    }

    override fun pushBack(element: E) {
        while (true) {
            val curCore = core.value;
            val newCore = curCore.pushBack(element)
            if (core.compareAndSet(curCore, newCore) )
            {
                return
            }
        }
    }

    override val size: Int get() = core.value.size
}

private class Core<E>(
    val capacity: Int,
) {
    private val array = atomicArrayOfNulls<E>(capacity)
    private val _size = atomic(capacity)

    val size: Int = _size.value

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E {
        require(index < size)
        return array[index].value as E
    }

    fun put(index: Int, element: E) {
        require(index < size)
        array[index].value = element
    }

    fun pushBack(element: E): Core<E> {

        val newCore = Core<E>(capacity + 1)
        newCore.put(size, element)
        for (i in 0 until size) {
            if (array[i].value != null) {
                newCore.put(i, array[i].value!!)
            }
        }
        System.out.println("make core " + newCore.size + " from core " + capacity.toString())
        return newCore
    }
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME