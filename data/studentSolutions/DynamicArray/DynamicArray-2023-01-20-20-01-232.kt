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
    private val _core = atomic(Core<E>(INITIAL_CAPACITY))

    @Suppress("UNCHECKED_CAST")
    override fun get(index: Int): E {
        require(index < size)
        return _core.value.array[index].value as E

    }

    override fun put(index: Int, element: E) {
        require(index < size)
        var core = _core.value
        while (true) {
            core.array[index].value = element
            core = core.next.value ?: break
        }
    }

    override fun pushBack(element: E) {
        while (true) {
            val core = _core.value
            val size = core.size.value
            val capacity = core.array.size
            if (size == capacity) {
                val nextCore = Core<E>(capacity * 2)
                nextCore.size.value = size
                for (i in 0 until capacity) {
                    nextCore.array[i].value = core.array[i].value
                }
                _core.compareAndSet(core, nextCore)
            } else if (core.size.compareAndSet(size, size + 1)) {
                core.array[size].value = element
                return
            }
        }
    }

    override val size: Int get() = _core.value.size.value
}

private class Core<E>(
    capacity: Int,
) {
    val array = atomicArrayOfNulls<E>(capacity)
    val size = atomic(0)

    val next = atomic<Core<E>?>(null)
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME