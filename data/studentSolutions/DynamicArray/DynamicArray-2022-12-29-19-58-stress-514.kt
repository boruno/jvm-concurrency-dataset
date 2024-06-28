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
        if (index >= size) {
            throw IllegalArgumentException()
        }
        var curCore = core.value
        curCore.array[index].value = element

        while (curCore.next.value != null) {
            curCore = curCore.next.value!!
            curCore.array[index].value = element
        }
    }

    override fun pushBack(element: E) {
        while (true) {
            val curCore = core.value
            if (curCore.size >= curCore.capacity) {
                val newCore = Core<E>(curCore.capacity + 1)
                newCore.incSize(0, curCore.size)
                for (i in 0..curCore.size - 1) {
                    newCore.array[i].compareAndSet(null, curCore.array[i].value)
                }
                curCore.next.compareAndSet(null, newCore)
                core.compareAndSet(curCore, newCore)
            } else {
                if (curCore.array[curCore.size].compareAndSet(null, element)) {
                    curCore.incSize(curCore.size, curCore.size + 1)
                    return
                }
                curCore.incSize(curCore.size, curCore.size + 1)
            }
        }
    }

    override val size: Int get() = core.value.size
}

private class Core<E>(
    val capacity: Int,
) {
    val array = atomicArrayOfNulls<E?>(capacity)
    private val _size = atomic(0)
    val next = atomic<Core<E>?>(null)
    val size: Int = _size.value

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E {
        require(index < size)
        return array[index].value as E
    }

    fun incSize(oldSize: Int, newSize: Int): Boolean {
        return _size.compareAndSet(oldSize, newSize)
    }
}

interface Cell<E> {
}

private class Empty<E>(val value: E? = null) : Cell<E>

private class Moved<E>(
    val value: E?,
) : Cell<E>

private class Fixed<E>(
    val value: E?,
) : Cell<E>

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME