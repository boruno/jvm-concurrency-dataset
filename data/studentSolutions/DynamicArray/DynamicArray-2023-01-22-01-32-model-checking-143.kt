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
        if ((index < 0) || (index >= size)) {
            throw IllegalArgumentException("Index is out of bounds")
        }
        var result: E?
        while (true) {
            result = core.value.array[index].value
            if (result == null) continue
            return result
        }
    }

    override fun put(index: Int, element: E) {
        if ((index < 0) || (index >= size)) {
            throw IllegalArgumentException("Index is out of bounds")
        }
        while (true) {
            val value = core.value.array[index].value
            if (value == null) continue
            if (core.value.array[index].compareAndSet(value, element)) return
        }
    }

    override fun pushBack(element: E) {
        val index = core.value.size.getAndIncrement()
        while (true) {
            val oldCore = core.value
            if (index < oldCore.capacity) {
                if (oldCore.array[index].compareAndSet(null, element)) return
            }
            if (!oldCore.resizing.compareAndSet(false, true)) continue
            val updatedCore = Core<E>(2 * oldCore.capacity)
            for (i in (0 until oldCore.capacity)) {
                while(true) {
                    val oldElement = oldCore.array[i].getAndSet(null)
                    if (oldElement == null) continue
                    updatedCore.array[i].value = oldElement
                    break
                }
            }
            core.compareAndSet(oldCore, updatedCore)
        }

    }

    override val size: Int get() = core.value.size.value
}

private class Core<E>(
    val capacity: Int,
) {
    val array = atomicArrayOfNulls<E>(capacity)
    val size = atomic(0)
    val resizing: AtomicBoolean = atomic(false)
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME