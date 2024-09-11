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
    private val core = atomic(Core<E>(INITIAL_CAPACITY, null))
    override fun get(index: Int): E = core.value.get(index)

    override fun put(index: Int, element: E) {
        var head = core.value
        if (index >= head.size.value) throw IllegalArgumentException()
        head.array[index].getAndSet(element)
        while (true) {
            if (head.get(index) != null && head.next.value != null) {
                head.next.value!!.array[index].getAndSet(head.get(index))
                head = head.next.value!!
            } else return
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
                } else head.size.compareAndSet(size, size + 1)
            } else {
                val newNode = Core<E>(2 * head.capacity, null)
                newNode.size.getAndSet(head.capacity)
                if (head.next.compareAndSet(null, newNode)) move(head, newNode)
                else if (head.next.value != null) move(head, head.next.value!!)
            }
        }
    }

    override val size: Int get() = core.value.size.value

    private fun move(head: Core<E>, newNode: Core<E>) {
        for (i in 1 until head.capacity) if (head.get(i - 1) != null) newNode.array[i - 1].compareAndSet(null, head.get(i - 1))
        core.compareAndSet(head, newNode)
    }
}

private class Core<E>(var capacity: Int, next: Core<E>?) {
    val array = atomicArrayOfNulls<E>(capacity)
    val size = atomic(0)
    val next = atomic(next)
    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E {
        require(index < size.value)
        return array[index].value as E
    }
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME