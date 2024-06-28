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

    override fun get(index: Int): E {
        var curCore = core.value
        while (true) {
            val result = curCore.get(index)
            if (result != null) {
                return result
            }
            val nextCore = curCore.nextCore.value
            if (nextCore != null) {
                curCore = nextCore
                if (nextCore.filled) {
                    core.compareAndSet(curCore, nextCore)
                }
            } else { // impossible
                throw Exception("Impossible 1")
            }
        }
    }

    override fun put(index: Int, element: E) {
        var curCore = core.value
        while (true) {
            if (curCore.put(index, element)) {
                return
            }
            val nextCore = curCore.nextCore.value
            if (nextCore != null) {
                curCore = nextCore
                if (nextCore.filled) {
                    core.compareAndSet(curCore, nextCore)
                }
            } else { // impossible
                throw Exception("Impossible 2")
            }
        }
    }

    override fun pushBack(element: E) {
        var curCore = core.value
        while (true) {
//            var curCore = core.value
            if (curCore.pushBack(element)) {
                return
            } else {
                val nextCore = curCore.nextCore.value
                if (nextCore != null) {
                    curCore = nextCore
                    // move core if next is filled
                    if (nextCore.filled) {
                        core.compareAndSet(curCore, nextCore)
                    }
                } else {
                    val newCore = Core<E>(curCore.capacity * 2, curCore.capacity)
                    if (curCore.nextCore.compareAndSet(null, newCore)) {
                        curCore.copyTo(newCore)
                        // move core if next is filled
                        core.compareAndSet(curCore, newCore)
                        curCore = newCore
                    }
                }
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
    private var _filled = false

    val size: Int get() = _size.value
    val filled: Boolean get() = _filled

    fun get(idx: Int): E? {
        require(idx < size)
        if (idx >= capacity) {
            return null
        }
        if (array[idx].value!!.state.value > State.FIXED) {
            return null
        }
        return array[idx].value!!.value
    }

    fun put(idx: Int, element: E): Boolean {
        require(idx < size)
        if (idx >= capacity) {
            return false
        }

        while (true) {
            val curNode = array[idx].value
            if (curNode != null) {
                if (curNode.state.value > State.NORMAL) {
                    return false
                }
            }
            if (array[idx].compareAndSet(curNode, Node(element))) {
                return true
            }
        }
    }

    fun pushBack(element: E): Boolean {
        var curSize = size
        while (true) {
            if (curSize < capacity) {
                if (array[curSize].compareAndSet(null, Node(element))) {
                    _size.compareAndSet(curSize, curSize + 1)
                    return true
                } else {
                    _size.compareAndSet(curSize, curSize + 1)
                }
            } else {
                if (_size.compareAndSet(curSize, curSize + 1)) {
                    break
                }
            }
            curSize = size
        }
        return false
    }

    fun copyTo(to: Core<E>) {
        for (idx in 0 until capacity) {
            while (true) {
                val curNode = array[idx].value ?: break
                val newNode = Node(curNode.value)
                newNode.state.value = State.FIXED
                if (array[idx].compareAndSet(curNode, newNode)) {
                    to.fallThrough(idx, curNode.value)
                    newNode.state.value = State.MOVED
                    break
                }
            }
        }
        to._filled = true
    }

    fun fallThrough(idx: Int, element: E) { // copy to this and if newer exist to them but only if no values present
        val newNode = Node(element)
        if (!array[idx].compareAndSet(null, newNode)) return

        if (nextCore.value != null) {
            if (newNode.state.compareAndSet(State.NORMAL, State.FIXED)) {
                nextCore.value!!.fallThrough(idx, element)
                newNode.state.compareAndSet(State.FIXED, State.MOVED)
            }
        }
    }
}

data class Node<E>(val value: E) {
    val state = atomic(State.NORMAL)
}

enum class State {
    NORMAL,
    FIXED,
    MOVED
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME