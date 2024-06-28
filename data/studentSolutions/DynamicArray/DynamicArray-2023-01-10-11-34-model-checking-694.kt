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
    private val core = atomic(Core<E>(INITIAL_CAPACITY, 0, null))
    override fun get(index: Int): E = core.value.get(index)

    private fun check(index: Int): Result<Unit> =
        if (index >= size || index < 0) Result.failure(IllegalArgumentException())
        else Result.success(Unit)

    override fun put(index: Int, element: E) {
        var head = core.value
        check(index)
        head.array[index].getAndSet(element)
        while (true) {
            val nextNode = head.next.value
            when {
                nextNode != null -> {
                    val y = head.array[index].value
                    if (y != null) {
                        nextNode.array[index].getAndSet(y)
                    }
                    head = nextNode
                }
                else -> return
            }
        }
    }

    override fun pushBack(element: E) {
        while (true) {
            val head = core.value
            val size = head.size.value
            if (size < head.capacity) {
                if (head.array[size].compareAndSet(null, element)) {
                    head.size.compareAndSet(size, size + 1)
                    return
                } else {
                    head.size.compareAndSet(size, size + 1)
                }
            } else {
                val newNode = Core<E>(2 * head.capacity, head.capacity, null)
                if (head.next.compareAndSet(null, newNode)) {
                    make(head, newNode)
                } else if (head.next.value != null) {
                    val nextNode = head.next.value!!
                    make(head, nextNode)
                }
            }
        }
    }

    override val size: Int get() = core.value.size.value

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

private class Core<E>(var capacity: Int, length: Int, next: Core<E>?) {
    val array = atomicArrayOfNulls<E>(capacity)
    val size = atomic(length)
    val next = atomic(next)

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E {
        require(index < size.value)
        return array[index].value as E
    }
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME