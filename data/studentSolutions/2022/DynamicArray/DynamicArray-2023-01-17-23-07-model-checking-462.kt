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
        val core = core.value
        val cur = core.get(index)
        val next_core = core.next.value
        if (next_core != null) {
            return next_core.get(index) ?: return cur
        } else {
            return cur
        }
    }

    override fun put(index: Int, element: E) {
        var cur_core = core.value
        while (true) {
            cur_core.put(index, element)
            cur_core = cur_core.next.value ?: return
        }
    }

    override fun pushBack(element: E) {
        while (true) {
            val cur_core = core.value
            val size = cur_core.size.value
            if (size < cur_core.capacity) {
                if (cur_core.tryPushBack(size, element)) {
                    return
                }
                cur_core.incrementSize(size)
            } else {
                val next = Core<E>(cur_core.capacity * 2, size)
                cur_core.next.compareAndSet(null, next)
                for (i in 0 until cur_core.capacity) {
                    cur_core.next.value!!.compareAndSet(i, null, cur_core.get(i))
                }
                core.compareAndSet(cur_core, cur_core.next.value!!)
            }
        }
    }

    override val size: Int get() = core.value.size.value
}

private class Core<E>(
    val capacity: Int,
    //size: Int = 0
    size: Int = capacity
) {
    private val array = atomicArrayOfNulls<E>(capacity)
    val size = atomic(size)
    val next: AtomicRef<Core<E>?> = atomic(null)

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E {
        require(index < size.value)
        return array[index].value as E
    }

    fun compareAndSet(index: Int, expect: E?, element: E?): Boolean {
        require(index < size.value)
        return array[index].compareAndSet(expect, element)
    }

    fun put(index: Int, element: E?) {
        require(index < size.value)
        array[index].lazySet(element)
    }

    fun incrementSize(expectedSize: Int) {
        size.compareAndSet(expectedSize, expectedSize + 1)
    }

    fun tryPushBack(index: Int, element: E?): Boolean {
        val cur_size = size.value
        if (cur_size != index) {
            return false
        }
        if (array[index].compareAndSet(null, element)) {
            incrementSize(cur_size)
            return true
        }
        return false
    }
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME