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
    private val core = atomic(Core<E>(0))

    override fun get(index: Int): E {
        val ans = core.value.get(index)
        return ans
    }

    override fun put(index: Int, element: E) {
        core.value.put(index, element)
    }

    override fun pushBack(element: E) {
        while (true) {
            val size = size
            var curCore: Core<E>;
            var nextCore: Core<E>
            while (true) {
                curCore = core.value
                if (curCore.next.value == null) {
                    nextCore = Core<E>(size + 1)
                    break;
                }
            }
            core.value.next.value = nextCore;
            for (i in 0 until size) {
                if (core.value.next.value!!.array[i].compareAndSet(null, curCore.array[i].value)) {
                    break;
                }
            }
            while (true) {
                if (core.value.next.value!!.array[size].compareAndSet(null, element)) {
                    break
                }
            }
            if (core.compareAndSet(curCore, nextCore)) {
                return
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
    val next = atomic<Core<E>?>(null)

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