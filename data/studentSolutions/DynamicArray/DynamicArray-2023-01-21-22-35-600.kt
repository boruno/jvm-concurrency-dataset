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
    private val head: AtomicRef<Core<E>> = atomic(Core(INITIAL_CAPACITY, 0, null))

    override fun get(index: Int): E {
        val curHead = head.value
        if (index >= curHead._size.value || index < 0) {
            throw IllegalArgumentException()
        }
        return curHead.array[index].value!!
    }

    override fun put(index: Int, element: E) {
        var curHead = head.value
        if (index >= curHead._size.value) {
            throw IllegalArgumentException()
        }
        curHead.array[index].compareAndSet(curHead.array[index].value,element)
        while (true) {
            val nextNode = curHead.next.value
            if (nextNode == null) {
                return
            } else {
                val y = curHead.array[index].value
                if (y != null) {
                    nextNode.array[index].compareAndSet(nextNode.array[index].value, y)
                }
                curHead = nextNode
            }

        }
    }

    override fun pushBack(element: E) {
        while (true) {
            val curHead = head.value
            val curSize = curHead._size.value
            if (curSize < curHead.getCapacity()) {
                if (curHead.array[curSize].compareAndSet(null, element)) {
                    curHead._size.compareAndSet(curSize, curSize + 1)
                    return
                } else {
                    curHead._size.compareAndSet(curSize, curSize + 1)
                }
            } else {
                val newNode =
                    Core<E>(2 * curHead.getCapacity(), curHead.getCapacity(), null)
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

    private fun make(curHead: Core<E>, nextNode: Core<E>) {
        for (i in 1..curHead.getCapacity()) {
            val y = curHead.array[i - 1].value
            if (y != null) {
                nextNode.array[i - 1].compareAndSet(null, y)
            }
        }
        head.compareAndSet(curHead, nextNode)
    }

    override val size: Int
        get() {
            return head.value._size.value
        }
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME

class Core<E>(private val capacity: Int, size: Int, next: Core<E>?) {
    val array = atomicArrayOfNulls<E>(capacity)
    val _size: AtomicInt = atomic(size)
    val next: AtomicRef<Core<E>?> = atomic(next)

    fun getCapacity(): Int {
        return capacity
    }
}