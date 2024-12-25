//package mpp.dynamicarray

import kotlinx.atomicfu.*
import java.lang.IllegalArgumentException

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
    private val core = atomic(Core<E>(INITIAL_CAPACITY, 0))

    override fun get(index: Int): E = core.value.get(index)

    override fun put(index: Int, element: E) {
        core.value.put(index, element)
    }

    override fun pushBack(element: E) {
        while (true) {
            val currentCore = core.value
            if (currentCore.pushBack(element)) {
                return
            }

            val currentNext = currentCore.next.value

            if (currentNext != null) {
                moveElements(currentCore, currentNext)
                core.compareAndSet(currentCore, currentNext)
                continue
            }

            val newCore = Core<E>(currentCore.capacity * 2, currentCore.capacity)
            if (!currentCore.next.compareAndSet(null, newCore)) {
                continue
            }

            moveElements(currentCore, newCore)

            core.compareAndSet(currentCore, newCore)
        }
    }

    private fun moveElements(oldCore: Core<E>, newCore: Core<E>) {
        for (i in 0 until oldCore.size) {
            try {
                newCore.casArray(i, null, oldCore.get(i))
            } catch (e: IllegalArgumentException) {
                continue
            }
        }
    }

    override val size: Int get() = core.value.size
}

private class Core<E>(
    val capacity: Int,
    initialSize: Int
) {
    private val array = atomicArrayOfNulls<E>(capacity)
    private val _size = atomic(initialSize)

    val next: AtomicRef<Core<E>?> = atomic(null)

    val size: Int
        get() = _size.value

    fun casArray(index: Int, expect: E?, value: E): Boolean {
        return array[index].compareAndSet(expect, value)
    }

    fun get(index: Int): E {
        require(index < size)
        val element = array[index].value
        require(element != null)
        return element
    }

    fun put(index: Int, element: E) {
        require(index < size)
        array[index].getAndSet(element)
    }

    fun pushBack(element: E): Boolean {
        while (true) {
            val currentSize = size

            if (currentSize >= capacity) {
                return false
            }

            if (!_size.compareAndSet(currentSize, currentSize + 1)) {
                continue
            }

            array[currentSize].getAndSet(element)
            return true
        }
    }
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME