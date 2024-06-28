package mpp.dynamicarray

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

            if (core.value.trySet(index, currentElement, element))
                break
        }
    }

    override fun pushBack(element: E) {
        val oldSize = size
        val newSize = calculateNewSize(oldSize)

        val oldCore = core.value
        val newCore = Core<E>(newSize)

        for (i in 0 until oldSize) {
            newCore.trySet(i, null, oldCore.get(i))
        }

        newCore.casElement(oldSize, null, element)

        if (core.compareAndSet(core.value, newCore)) {
            return
        } else {
            var pushBackIndex = 0

            while (true) {
                if (core.value.casElement(pushBackIndex, null, element))
                    break

                pushBackIndex++
            }
        }
    }

    override val size: Int get() = core.value.size

    private fun calculateNewSize(currentSize: Int): Int {
        return if (currentSize != 0) currentSize * 2 else 1
    }
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

    fun trySet(index: Int, currentElement: E?, element: E?): Boolean {
        require(index < size)
        return array[index].compareAndSet(currentElement, element)
    }

    fun casElement(index: Int, currentElement: E?, element: E?): Boolean {
        if (array[index].compareAndSet(currentElement, element)) {
            _size.getAndIncrement()
            return true
        } else return false
    }
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME