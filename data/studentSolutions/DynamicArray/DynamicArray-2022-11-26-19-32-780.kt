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
    private val next = atomic<Core<E>?>(null)
    private val pre_size = atomic(0)
    private val _size = atomic(0)

    override fun get(index: Int): E {
        while (true) {
            require(index < size)
            val value = core.value.array[index].value
            if (value != null) {
                return value
            }
            val nextArray = next.value ?: continue
            return nextArray.array[index].value ?: continue
        }
    }

    override fun put(index: Int, element: E) {
        while (true) {
            require(index < core.value.size.value)
            val value = core.value.array[index].value
            if (value != null) {
                if (!core.value.array[index].compareAndSet(value, element)) {
                    continue
                }
                return
            }
            val nextArray = next.value ?: continue
            nextArray.array[index].value = element
            return
        }
    }

    override fun pushBack(element: E) {
        val index = pre_size.getAndIncrement()
        while (true) {
            val currentArray = core.value
            if (index >= currentArray.capacity) {
                new()
            } else {
                currentArray.array[index].compareAndSet(null, element)
                _size.incrementAndGet()
                return
            }
        }
    }

    private fun new() {
        val currentArray = core.value
        val capacity = currentArray.array.size
        next.compareAndSet(null, Core(capacity * 2))
        val nextArray = next.value ?: return
        nextArray.size.compareAndSet(0, currentArray.size.value)
        for (i in 0 until capacity) {
            val v = currentArray.array[i].getAndSet(null)
            nextArray.array[i].compareAndSet(null, v)
        }
        if (next.getAndSet(null) != null) {
            core.compareAndSet(currentArray, nextArray)
        }
    }


    override val size: Int get() = _size.value
}

private class Core<E>(
    capacity: Int,
) {
    val array = atomicArrayOfNulls<E>(capacity)
    val size = atomic(0)
    val capacity: Int get() = array.size
}


private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME