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

    override fun get(index: Int): E = core.value.get(index)

    override fun put(index: Int, element: E) {
        var head: Core<E> = core.value
        core.value.put(index, element)

        while (true) {
            val nextNode = head.next.value ?: break
            val value = head.get(index) ?: break
            nextNode.put(index, value)
            head = nextNode
        }
    }

    override fun pushBack(element: E) {
        while (true) {
            val oldCore = core.value
            val size = oldCore.size.value
            val oldCapacity = oldCore.capacity()
            if (size < oldCapacity) {
                if (oldCore.cas(size, null, element)) {
                    oldCore.cas(size, size + 1)
                    return
                } else {
                    oldCore.cas(size, size + 1)
                }
            }
            else if (size == oldCapacity) {
                var newCore: Core<E> = Core(2 * oldCore.capacity(), oldCore.capacity())
                if (!oldCore.next.compareAndSet(null, newCore)) {
                    newCore = oldCore.next.value!!
                }

                for (i in 0 until oldCore.capacity()) {
                    val oldValue = oldCore.get(i) ?: continue
                    newCore.cas(i, null, oldValue)
                }

                core.compareAndSet(oldCore, newCore)
            }
        }
    }

    override val size: Int get() = core.value.size.value
}

private class Core<E>(
    capacity: Int,
    size: Int = 0,
    next: Core<E>? = null
) {
    private val array = atomicArrayOfNulls<E>(capacity)
    val size = atomic(size)
    val next = atomic(next)

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E {
        require(index < size.value)
        return array[index].value as E
    }

    fun put(index: Int, value: E): E? {
        require(index < size.value)
        return array[index].getAndSet(value)
    }

    fun capacity(): Int {
        return array.size
    }

    fun cas(index: Int, expected: E?, update: E?): Boolean {
        require(index < size.value)
        return array[index].compareAndSet(expected, update)
    }

    fun cas(expected: Int, update: Int): Boolean {
        require(size.value <= array.size)
        return size.compareAndSet(expected, update)
    }
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME