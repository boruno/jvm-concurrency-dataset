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
    private val core = atomic(Core<E>(INITIAL_CAPACITY))

    override fun get(index: Int): E = core.value.get(index)

    override fun put(index: Int, element: E) {
        while (true) {
            val currentElement = get(index)

            if (core.value.casElement(index, currentElement, element))
                break
        }
    }

    override fun pushBack(element: E) {
        val oldSize = size
        val newSize = oldSize * 2
        val oldCore = core.value
        val newCore = Core<E>(newSize)

        for (i in 0 until oldSize) {
            newCore.casElement(i, null, oldCore.get(i))
        }

        newCore.casElement(oldSize, null, element)

        if (core.compareAndSet(core.value, newCore)) {
            return
        } else {
            var pushBackIndex = oldSize

            while (true) {
                if (core.value.casElement(pushBackIndex, null, element))
                    break

                pushBackIndex++
            }
        }
    }

    override val size: Int get() = core.value.size
}

private class Core<E>(
    capacity: Int,
) {
    private val array = atomicArrayOfNulls<E>(capacity)
    private val _size = atomic(0)

    val size: Int = _size.value

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E {
        require(index < size)
        return array[index].value as E
    }

    fun casElement(index: Int, currentElement: E?, element: E?): Boolean {
        require(index < size)
        return array[index].compareAndSet(currentElement, element)
    }
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME