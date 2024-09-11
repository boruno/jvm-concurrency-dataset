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

    override fun get(index: Int): E {
        val curHead = core.value
        if (index >= curHead.size.value) throw IllegalArgumentException()
        val x = curHead.array[index].value
        if (x != null) return x
        else throw RuntimeException("unexpected case")
    }

    override fun put(index: Int, element: E) {
        var cur_head = core.value
        if (index >= cur_head.size.value) throw IllegalArgumentException()
        cur_head.array[index].getAndSet(element)
        while (true) {
            val nextNode = cur_head.next.value
            when {
                nextNode != null -> {
                    val y = cur_head.array[index].value
                    if (y != null) {
                        nextNode.array[index].getAndSet(y)
                    }
                    cur_head = nextNode
                }
                else -> return
            }
        }
    }

    override fun pushBack(element: E) {
        while (true) {
            val cur_head = core.value
            val size = cur_head.size.value
            if (size < cur_head.getCapacity()) {
//                cur_head.size.compareAndSet(size, size + 1)
                if (cur_head.array[size].compareAndSet(null, element)) {
                    cur_head.size.compareAndSet(size, size + 1)
                    return
                } else {
                    cur_head.size.compareAndSet(size, size + 1)
                }
            } else {
                val newNode = Core<E>(2 * cur_head.getCapacity(), cur_head.getCapacity(), null)
                if (cur_head.next.compareAndSet(null, newNode)) make(cur_head, newNode)
                else {
                    val nextNode = cur_head.next.value
                    if (nextNode != null) make(cur_head, nextNode)
                }
            }
        }
    }

    private fun make(curHead: Core<E>, nextNode: Core<E>) {
        for (i in 1 until curHead.getCapacity()) {
            val y = curHead.array[i - 1].value
            if (y != null) nextNode.array[i - 1].compareAndSet(null, y)
        }
        core.compareAndSet(curHead, nextNode)
    }

    override val size: Int get() = core.value.size.value
}


class Core<T>(private val capacity: Int, size: Int, next: Core<T>?) {
    val array: AtomicArray<T?> = atomicArrayOfNulls<T>(capacity)
    val size: AtomicInt = atomic(size)
    val next: AtomicRef<Core<T>?> = atomic(next)

    fun getCapacity(): Int {
        return capacity
    }
}


private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME
