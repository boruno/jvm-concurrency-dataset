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
    private val core = atomic(Core<E>(INITIAL_CAPACITY, 0))
    private val next: AtomicRef<Core<E>?> = atomic(null)

    override fun get(index: Int): E = core.value.get(index)!!.elem

    override fun put(index: Int, element: E) {
        val newNode = Core.Node(element)
        while (true) {
            val curCore = core.value
            val currVal = curCore.get(index)
            if (currVal !is Core.Removed) {
                if (curCore.array[index].compareAndSet(currVal, newNode)) {
                    return
                }
            }
        }
    }

    override fun pushBack(element: E) {
        val newNode = Core.Node(element)
        while (true) {
            val curCore = core.value
            val currSize = curCore.size
            if (currSize == curCore.capacity) {
                resize(curCore)
            } else {
                if (curCore.array[currSize].compareAndSet(null, newNode)) {
                    curCore._size.compareAndSet(currSize, currSize + 1)     // may fail if we've been helped
                    return
                } else {
                    curCore._size.compareAndSet(currSize, currSize + 1)
                }
            }
        }
    }


    private fun resize(prevCore: Core<E>) {
        val prevSize = prevCore.capacity
        val newCore = Core<E>(prevSize * 2, prevSize)
        if (next.compareAndSet(null, newCore)) {
            for (i in 0 until prevSize) {
                while(true) {
                    val currVal = prevCore.array[i].value!!
                    val removed = Core.Removed(currVal)
                    if (prevCore.array[i].compareAndSet(currVal, removed)) {
                        newCore.array[i].compareAndSet(null, Core.Node(currVal.elem))
                        break
                    }
                }
            }
            core.compareAndSet(prevCore, newCore)
            next.compareAndSet(newCore, null)
        }
    }


    override val size: Int get() = core.value.size
}

private class Core<E>(
    val capacity: Int,
    curSize: Int
) {
    val array = atomicArrayOfNulls<Node<E>>(capacity)
    val _size = atomic(curSize)

    val size: Int = _size.value

    open class Node<E>(val elem: E) {
        constructor(node: Node<E>) : this(node.elem)
    }

    class Removed<E>(node: Node<E>) : Node<E>(node)

    fun get(index: Int): Node<E>? {
        require(index < size)
        return array[index].value
    }
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME