package mpp.dynamicarray

import kotlinx.atomicfu.*
import java.util.*

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
        core.value.put(index, element)
    }

    override fun pushBack(element: E) {
        core.value.pushBack(element)
    }

    override val size: Int get() = core.value.size
}

private class Core<E>(
    capacity: Int,
) {
    private val array = atomicArrayOfNulls<E>(capacity)
    private val _size = atomic(capacity)

    private final val next: AtomicRef<Core<E>?> = atomic(null)

    val size: Int = if (next.value == null) _size.value else next.value!!.size

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E {
        require(index < size)
        if (index >= _size.value) {
            if (next.value != null)
                return next.value!!.get(index)
        }
        return array[index].value as E
    }

    @Suppress("UNCHECKED_CAST")
    fun put(index: Int, element: E) {
        require(index < size)
        if (index >= _size.value) {
            next.value?.put(index, element)
            return
        }
        array[index].value = element
    }

    fun pushBack(element: E) {
        if (next.value != null) {
            next.value!!.pushBack(element)
            return
        }
        val newCore = Core<E>(size + 1)
        for (i in 0 until size) {
            newCore.put(i, get(i))
        }
        newCore.put(size, element)
        next.compareAndSet(null, newCore)
    }
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME