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
        var curHead = core.value
        while (true) {
            curHead.put(index, element)
            curHead = curHead.next.value ?: return
        }
    }

    override fun pushBack(element: E) {
        while (true) {
            val curHead = core.value
            val size = curHead._size.value
            val capacity = curHead.capacity
            if (size < capacity) {
                if (curHead.safePut(size, element)) {
                    curHead._size.compareAndSet(size, size + 1)
                    return
                }
                curHead._size.compareAndSet(size, size + 1)
            }
            val newHead = Core<E>(capacity * 2)
            newHead._size.compareAndSet(0, size)
            curHead.next.compareAndSet(null, newHead)
            for (i in 0 until size) {
                curHead.next.value?.safePut(i, curHead.get(i))
            }
            core.compareAndSet(curHead, curHead.next.value!!)
        }
    }

    override val size: Int get() = core.value.size
}

private class Core<E>(
    val capacity: Int,
) {
    private val array = atomicArrayOfNulls<E>(capacity)
    val _size = atomic(0)
    val next: AtomicRef<Core<E>?> = atomic(null)

    val size: Int = _size.value

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E {
        require(index < size)
        return array[index].value as E
    }
    fun put(index: Int, element: E) {
        require(index < size)
        array[index].getAndSet(element)
    }

    fun safePut(index: Int, element: E): Boolean {
        return array[index].compareAndSet(null, element)
    }
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME