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
    private val core = atomic(Core<E>(INITIAL_CAPACITY + 100))

    override fun get(index: Int): E = core.value.get(index)

    override fun put(index: Int, element: E) {
        if (index < size) {
            throw IllegalArgumentException("index < size")
        }
        core.value.array[index].value = element
    }

    override fun pushBack(element: E) {
        // val capacity = core.value.array.size
        // if (size >= capacity) {
        //     val core2 = atomic(Core<E>(capacity * 2))
        //     for (i in 0 until capacity) {
        //         core2.value.array[i].value = core.value.array[i].value
        //     }
        //     core.value = core2.value
        // }
        // core.value.array[size].value = element
        // core.value._size.incrementAndGet()
    }

    override val size: Int get() = core.value.size
}

private class Core<E>(
    capacity: Int,
) {
    val array = atomicArrayOfNulls<E>(capacity)
    val _size = atomic(0)

    val size: Int = _size.value

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E {
        require(index < size)
        return array[index].value as E
    }
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME