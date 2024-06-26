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

    override fun get(index: Int): E {
        require(index < size) { "Index $index is out of bounds for size $size" }
        return core.value.get(index)
    }

    override fun put(index: Int, element: E) {
        val currentCore = core.value
        require(index < size) { "Index $index is out of bounds for size $size" }
        while (true) {
            val value = currentCore.get(index)
            if (currentCore.cas(index, value, element)) return
        }
    }

    override fun pushBack(element: E) {
        val currentCore = core.value
        while (true) {
            if (size < currentCore.capacity) {
                if (currentCore.cas(size, null, element)) {
                    currentCore.faa()
                    return
                }
            } else {
                val newCore = Core<E>(currentCore.capacity * 2)
                for (i in 0 until currentCore.capacity) {
                    newCore.cas(i, null, currentCore.get(i))
                    newCore.faa()
                }
                newCore.cas(currentCore.capacity, null, element)
                newCore.faa()
                if (core.compareAndSet(currentCore, newCore)) return
            }
        }
    }

    override val size: Int get() = core.value.size
}

private class Core<E>(
    val capacity: Int,
) {
    private val array = atomicArrayOfNulls<E>(capacity)
    private val _size = atomic(0)

    val size: Int get() = _size.value

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E {
        require(index < size)
        return array[index].value as E
    }

    fun cas(index: Int, expected: E?, actual: E?): Boolean = array[index].compareAndSet(expected, actual)

    fun faa(): Int = _size.getAndIncrement()
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME