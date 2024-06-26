package mpp.dynamicarray

import kotlinx.atomicfu.*
import kotlin.math.min

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

    override fun put(index: Int, element: E) = core.value.put(index, element)

    override fun pushBack(element: E) {
        val index = core.value.getNextIndex()

        do {
            val curCore = core.value
            val newCore = Core<E>(curCore.capacity * 2)
            newCore.put(curCore)
        } while (index >= core.value.capacity && !core.compareAndSet(curCore, newCore))
        core.value.tryPutAtomic(index, element)
    }

    override val size: Int get() = core.value.size
}

private class Core<E>(
    val capacity: Int,
) {
    private val array = atomicArrayOfNulls<E>(capacity)
    private val _size = atomic(0)

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

    fun put(core: Core<E>) {
        val amount = min(core.size, array.size)
        for (i in (0 until amount))
            array[i].value = core.get(i)
    }

    fun tryPutAtomic(index: Int, element: E) = array[index].compareAndSet(null, element)

    fun getNextIndex(): Int = _size.getAndIncrement()
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME