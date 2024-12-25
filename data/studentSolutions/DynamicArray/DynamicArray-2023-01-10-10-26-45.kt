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
    private val core = atomic(Core<E>(INITIAL_CAPACITY, 0, null))

    override fun get(index: Int): E = core.value.get(index)

    override fun put(index: Int, element: E) {
        var curHead = core.value
        if (index >= curHead.size) {
            throw IllegalArgumentException()
        }
        curHead.array[index].getAndSet(element)
        while (true) {
            val nextNode = curHead.next.value
            when {
                nextNode != null -> {
                    val y = curHead.array[index].value
                    if (y != null) {
                        nextNode.array[index].getAndSet(y)
                    }
                    curHead = nextNode
                }
                else -> return
            }
        }
    }

    override fun pushBack(element: E) {
        while (true) {
            val curHead = core.value
            val curSize = curHead.size
            if (curSize < curHead.capacity) {
                if (curHead.array[curSize].compareAndSet(null, element)) {
                    curHead._size.compareAndSet(curSize, curSize + 1)
                    return
                } else {
                    curHead._size.compareAndSet(curSize, curSize + 1)
                }
            } else {
                val newNode = Core<E>(2 * curHead.capacity, curHead.capacity, null)
                if (curHead.next.compareAndSet(null, newNode)) {
                    make(curHead, newNode)
                } else {
                    val nextNode = curHead.next.value
                    if (nextNode != null) {
                        make(curHead, nextNode)
                    }
                }
            }
        }
    }

    override val size: Int get() = core.value.size

    private fun make(curHead: Core<E>, nextNode: Core<E>) {
        for (i in 1..curHead.capacity) {
            val y = curHead.array[i - 1].value
            if (y != null) {
                nextNode.array[i - 1].compareAndSet(null, y)
            }
        }
        core.compareAndSet(curHead, nextNode)
    }
}

private class Core<E>(
    var capacity: Int, length: Int, next: Core<E>?
) {
    val array = atomicArrayOfNulls<E>(capacity)
    val _size = atomic(length)

    val size: Int = _size.value
    val next = atomic<Core<E>?>(next)

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E {
        require(index < size)
        return array[index].value as E
    }
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME