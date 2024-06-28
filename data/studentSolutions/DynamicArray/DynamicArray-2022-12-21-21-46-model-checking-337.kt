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

    val size: Int get() = _size.value

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E {
        require(index < size)
        if (index >= capacity) {
            return nextCore.value!!.get(index)
        }

        val node = array[index].value!!
        return if (node.state.value < NodeState.MOVED) {
            node.value
        } else {
            nextCore.value!!.get(index)
        }
    }

    fun put(index: Int, element: E) {
        require(index < size) //??
        if (index >= capacity) {
            nextCore.value!!.put(index, element)
            return
        }

        while (true) {
            val curNode = array[index].value
            if (curNode == null) {
                if (putIfNull(index, element, false)) {
                    return
                }
                continue
//                putIfNull(index, element) //???
            }
            // if not null rewrite
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

    /**
     * put to the latest core
     * do not overwrite if there is an element
     * make all elements on the way fixed, then moved if they
     * */
    private fun putIfNull(index: Int, element: E, push: Boolean): Boolean {
        val res: Boolean
        if (push && index >= capacity) {
            val newCore = Core<E>(capacity * 2, capacity)
            if (nextCore.compareAndSet(null, newCore)) {
                moveElements()
            }
            return nextCore.value!!.putIfNull(index, element, true)
        }

        val newNode = Node(element)
        if (push) {
//            while (true) {
                if (array[index].compareAndSet(null, newNode)) {//<<<<<<<<<<<<<<<<<<<
                    while (true) {
                        val curSize = size
                        if (_size.compareAndSet(curSize, curSize + 1)) {
                            if (size > index) {
                                break
                            }
                        }
                    }
                } else {
                    return false
                }
//            }
        } else {
            if (!array[index].compareAndSet(null, newNode)) return false
        }

        if (nextCore.value != null) {
            newNode.state.compareAndSet(NodeState.NORMAL, NodeState.FIXED)
            res = nextCore.value!!.putIfNull(index, element, push)
            newNode.state.compareAndSet(NodeState.FIXED, NodeState.MOVED)
        } else {
            res = true
        }

        return res
    }

    fun pushBack(element: E) {
        while (true) {
            val idx = size
            if (idx < capacity) { // -1
                if (array[idx].compareAndSet(null, Node(element))) {
                    _size.compareAndSet(idx, idx + 1)
                    return
                } else {
                    _size.compareAndSet(idx, idx + 1)
                }
            } else {
                if (putIfNull(idx, element, true)) {
                    _size.compareAndSet(idx, idx + 1)
                    return
                } else {
                    _size.compareAndSet(idx, idx + 1)
                }
            }
        }
    }

    fun moveElements() {
        for (idx in 0 until capacity) {
            while (true) {
                // N-th new core, value not updated from prev core or put, leave it like that
                val curNode = array[idx].value ?: break
                val newNode = Node(curNode.value)
                newNode.state.compareAndSet(NodeState.NORMAL, NodeState.FIXED)
                if (array[idx].compareAndSet(curNode, newNode)) {
                    nextCore.value!!.putIfNull(
                        idx,
                        curNode.value!!,
                        false
                    ) // in case there is a new move occurring in parallel
                    newNode.state.compareAndSet(NodeState.FIXED, NodeState.MOVED) // not CAS because cannot modify fixed
                    break
                }
            }
        }
        nextCore.value!!.filled = true
    }
}

private class Node<E>(val value: E) {
    val state = atomic(NodeState.NORMAL)
}

private enum class NodeState {
    NORMAL,
    FIXED,
    MOVED
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME