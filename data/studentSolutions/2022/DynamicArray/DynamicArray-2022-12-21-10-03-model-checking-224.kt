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

    override fun get(index: Int): E = core.value.get(index)

    override fun put(index: Int, element: E) = core.value.put(index, element)

    override fun pushBack(element: E) { // MUST BE ATOMIC
        core.value.pushBack(element)

        while (true) {
            val curCore = core.value
            val nextCore = curCore.nextCore.value ?: return
            if (nextCore.capacity > curCore.capacity && nextCore.filled) {
                core.compareAndSet(curCore, nextCore)
            } else {
                break
            }
        }
    }


    override val size: Int get() = core.value.size
}

private class Core<E>(
    val capacity: Int,
    initialSize: Int
) {
    private val array = atomicArrayOfNulls<Node<E>>(capacity)
    private val _size = atomic(initialSize)
    val nextCore = atomic<Core<E>?>(null)
    var filled = false

    val size: Int
        get() = _size.value

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E {
        require(index < size)
        val node = array[index].value!!
        return if (node.state.value < NodeState.MOVED) {
            array[index].value as E
        } else {
            nextCore.value!!.get(index)
        }
    }

    fun put(index: Int, element: E) {
        require(index < size) //??
        while (true) {
            if (array[index].compareAndSet(null, Node(element))) {
                //if here means in new not fully filled core, because index < size
                return
            }

            val curNode = array[index].value!! // if not null rewrite
            if (curNode.state.value == NodeState.NORMAL) {
                val newNode = Node(element)
                if (array[index].compareAndSet(curNode, newNode)) {
                    return
                }
            } else {
                nextCore.value!!.put(index, element)
                return
            }
        }
    }

    fun pushBack(element: E) {
        while (true) {
            val idx = size
            when {
                idx < capacity - 1 -> {
                    if (array[idx].compareAndSet(null, Node(element))) {
                        _size.compareAndSet(idx, idx + 1)
                        return
                    } else {
                        _size.compareAndSet(idx, idx + 1)
                    }
                }
                idx == capacity - 1 -> {
                    if (array[idx].compareAndSet(null, Node(element))) {
                        _size.compareAndSet(idx, idx + 1)
                    } else {
                        _size.compareAndSet(idx, idx + 1)
                        continue
                    }

                    val newCore = Core<E>(capacity * 2, capacity)
                    if (nextCore.compareAndSet(null, newCore)) {
                        moveElements()
                    }
                }
                idx >= capacity -> {
                    nextCore.value!!.pushBack(element)
                }
            }
        }
    }

    // first append then move
    fun moveElements() {
        for (idx in 0 until capacity) {
            while (true) {
                val curNode = array[idx].value ?: continue // N-th new core, value not updated from prev core or put
                val newNode = Node(curNode.value)
                newNode.state.compareAndSet(NodeState.NORMAL, NodeState.FIXED)
                if (array[idx].compareAndSet(curNode, newNode)) {
                    nextCore.value!!.array[idx].compareAndSet(
                        null,
                        newNode
                    ) // if not null then put occurred and already filled in new core
                    newNode.state.compareAndSet(NodeState.FIXED, NodeState.MOVED) // not CAS because cannot modify fixed
                    break
                }
            }
        }
        nextCore.value!!.filled = true
    }
}

private class Node<E>(val value: E?) {
    val state = atomic(NodeState.NORMAL)
}

private enum class NodeState {
    NORMAL,
    FIXED,
    MOVED
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME