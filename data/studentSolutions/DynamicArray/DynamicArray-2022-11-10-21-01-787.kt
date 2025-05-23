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
    private val nextCore = atomic<Core<E>?>(null)

    override fun get(index: Int): E = core.value.get(index)

    override fun put(index: Int, element: E) {
        core.value.put(index, element)
    }

    override fun pushBack(element: E) {
        while (true) {
            if (nextCore.value != null)
            {
                continue
            }
            val size = size
            val curCore = core.value;
            nextCore.value = Core<E>(size + 1)
            nextCore.value
            for (i in 0 until size) {
                if (curCore.array[i].value != null) {
                    while (true) {
                        if (nextCore.value!!.array[i].compareAndSet(null, curCore.array[i].value)) {
                            break;
                        }
                    }
                }
            }
            while (true) {
                if (nextCore.value!!.array[size].compareAndSet(null, element)) {
                    break
                }
            }
            var err = false
            for (i in 0 until size) {
                if (nextCore.value!!.array[size - 1 - i].value != core.value.array[size - 1 - i].value) {
                   err = true
                }
            }
            if (err) {
                continue
            }
            if (core.compareAndSet(curCore, nextCore.value!!)) {
                val next = nextCore.value
                if (nextCore.compareAndSet(next, null)) {
                    return
                }
            }
        }
    }

    override val size: Int get() = core.value.size
}

private class Core<E>(
    val capacity: Int,
) {
    val array = atomicArrayOfNulls<E>(capacity)
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
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME