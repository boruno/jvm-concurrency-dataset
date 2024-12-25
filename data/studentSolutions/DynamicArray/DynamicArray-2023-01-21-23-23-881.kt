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
    private val core: AtomicRef<Core<E>> = atomic(Core(INITIAL_CAPACITY, 0, null))

    override fun get(index: Int): E {
        val curCore = core.value
        if (index >= curCore._size.value || index < 0) {
            throw IllegalArgumentException()
        }
        return curCore.array[index].value!!
    }

    override fun put(index: Int, element: E) {
        var curCore = core.value
        if (index >= curCore.size || index < 0) {
            throw IllegalArgumentException()
        }
        curCore.array[index].compareAndSet(curCore.array[index].value, element)
        while (true) {
            val nextNode = curCore.next.value
            if (nextNode == null) {
                return
            } else {
                val value = curCore.array[index].value
                nextNode.array[index].getAndSet(value)
                curCore = nextNode
            }

        }
    }

    override fun pushBack(element: E) {
        while (true) {
            val curHead = core.value
            val curSize = curHead._size.value
            if (curSize < curHead.capacity) {
                if (curHead.array[curSize].compareAndSet(null, element)) {
                    curHead._size.compareAndSet(curSize, curSize + 1)
                    return
                } else {
                    curHead._size.compareAndSet(curSize, curSize + 1)
                }
            } else {
                val newNode = Core<E>(curHead.capacity * 2, curHead.capacity, null)
                if (curHead.next.compareAndSet(null, newNode)) {
                    for (i in 1..curHead.capacity) {
                        val value = curHead.array[i - 1].value
                        if (value != null) {
                            newNode.array[i - 1].compareAndSet(null, value)
                        }
                    }
                    core.compareAndSet(curHead, newNode)
                } else {
                    val nextNode = curHead.next.value
                    if (nextNode != null) {
                        for (i in 1..curHead.capacity) {
                            val value = curHead.array[i - 1].value
                            if (value != null) {
                                nextNode.array[i - 1].compareAndSet(null, value)
                            }
                        }
                        core.compareAndSet(curHead, nextNode)
                    }
                }
            }
        }
    }

    override val size: Int
        get() {
            return core.value._size.value
        }
}

class Core<E>(
    val capacity: Int,
    size: Int,
    next: Core<E>?
) {
    val array = atomicArrayOfNulls<E>(capacity)
    val _size: AtomicInt = atomic(size)
    val next: AtomicRef<Core<E>?> = atomic(next)

    val size: Int = _size.value

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E {
        require(index < size)
        return array[index].value as E
    }
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME