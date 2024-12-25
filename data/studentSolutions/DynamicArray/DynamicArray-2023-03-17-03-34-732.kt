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
        require(index < size && index >= 0)
        head.array[index].getAndSet(element)
        // while (true) {
        //     if (head.get(index) != null && head.next.value != null) {
        //         head.next.value!!.array[index].getAndSet(head.get(index))
        //         head = head.next.value!!
        //     } else return
        // }
    }

    override fun pushBack(element: E) {
        while (true) {
            if (size < head.capacity) {
                val oldSize = size
                if (head._size.compareAndSet(size, size + 1)) {
                    head.array[oldSize].getAndSet(element)
                    return
                } else {
                    continue
                }
            } else {
                addCapacity() 
                continue
            }
        }
    }

    private fun addCapacity() {
        val oldCore = core.value
        val newCore = Core<E>(2 * head.capacity)
        newCore._size.getAndSet(size)
        for (i in 0..size) {
            newCore.array[i].value = head.get(i)
        }
        core.compareAndSet(oldCore, newCore)
    }
        // while (true) {
        //     if (size < head.capacity) {
        //         if (head.array[size].compareAndSet(null, element)) {
        //             head.size.compareAndSet(size, size + 1)
        //             return
        //         } else head.size.compareAndSet(size, size + 1)
        //     } else {
        //         val newNode = Core<E>(2 * head.capacity, null)
        //         newNode.size.getAndSet(head.capacity)
        //         if (head.next.compareAndSet(null, newNode)) {
        //             for (i in 1..head.capacity) if (head.get(i - 1) != null) newNode.array[i - 1].compareAndSet(null, head.get(i - 1))
        //             core.compareAndSet(head, newNode)
        //         } else if (head.next.value != null) {
        //             for (i in 1..head.capacity) if (head.get(i - 1) != null) head.next.value!!.array[i - 1].compareAndSet(null, head.get(i - 1))
        //             core.compareAndSet(head, head.next.value!!)
        //         }
        //     }
        // }

    override val size: Int get() = head.size
    
    private val head: Core<E> get() = core.value
}

private class Core<E>(
    val capacity: Int,
) {
    private val _array = atomicArrayOfNulls<E>(capacity)
    val _size = atomic(0)

    val size: Int = _size.value

    val array: AtomicArray<E?> get() = _array

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E {
        require(index < size && index >= 0)
        return _array[index].value as E
    }
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME