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

    private val core = atomic(Core<Wrap<E>>(INITIAL_CAPACITY))
    private val sizeRef: AtomicInt = atomic(0)

    override fun get(index: Int): E {
        val size = sizeRef.value
        if (index >= size) {
            throw IllegalArgumentException("Index ${index} out of ${size} length")
        }
        val curCore = core.value
        val el = curCore.array[index].value
        return el?.data ?: throw IllegalStateException("WTF")
    }


    override fun put(index: Int, element: E) {
        while (true) {
            val size = sizeRef.value
            if (index >= size) {
                throw IllegalArgumentException("Index ${index} out of ${size} length")
            }
            val curCore = core.value
            val prevEl = curCore.array[index].value
            if (prevEl is N) {
                if (curCore.array[index].compareAndSet(prevEl, N(element))) {
                    return
                }
            } else {
                helpMove()
            }
        }
    }

    override fun pushBack(element: E) {
        while (true) {
            val curCore = core.value
            val size = sizeRef.value
            if (size >= curCore.array.size) {
                moveTable(curCore)
            } else {
                if (curCore.array[size].compareAndSet(null, N(element))) {
                    sizeRef.incrementAndGet()
                    return
                }
            }
        }
    }

    fun helpMove() {
        val curCore = core.value
        val nextCore = curCore.nextCore.value
        if (nextCore != null) {
            for (i in 0 until curCore.array.size) {
                moveElement(curCore, nextCore, i)
            }
            core.compareAndSet(curCore, nextCore)
        }
    }

    private fun moveTable(prevCore: Core<Wrap<E>>) {
        val prevCapacity = prevCore.array.size

        val newCapacity = prevCapacity * 2
        val newCore = Core<Wrap<E>>(newCapacity)

        if (!prevCore.nextCore.compareAndSet(null, newCore)) {
            helpMove()
            return
        }
        for (i in 0 until prevCapacity) {
            moveElement(prevCore, newCore, i)
        }
        core.compareAndSet(prevCore, newCore)
    }

    private fun moveElement(prevCore: Core<Wrap<E>>, newCore: Core<Wrap<E>>, index: Int) {
        while (true) {
            val oldValue = prevCore.array[index].value ?: throw IllegalStateException("WTF")
            if (prevCore.array[index].compareAndSet(oldValue, I(oldValue.data))) {
                newCore.array[index].compareAndSet(null, N(oldValue.data))
                break
            }
        }

    }

    override val size: Int
        get() {
            return sizeRef.value
        }
}

class Core<E>(
    capacity: Int,
) {
    val nextCore: AtomicRef<Core<E>?> = atomic(null)
    val array = atomicArrayOfNulls<E>(capacity)
}

interface Wrap<E> {
    val data: E
}

class N<E>(override val data: E) : Wrap<E>

class I<E>(override val data: E) : Wrap<E>

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME

/*
class DynamicArrayImpl<E> : DynamicArray<E> {
    private val core = atomic(Core<E>(INITIAL_CAPACITY))

    override fun get(index: Int): E = core.value.get(index)

    override fun put(index: Int, element: E) {
        TODO("Not yet implemented")
    }

    override fun pushBack(element: E) {
        TODO("Not yet implemented")
    }

    override val size: Int get() = core.value.size
}

private class Core<E>(
    capacity: Int,
) {
    private val array = atomicArrayOfNulls<E>(capacity)
    private val _size = atomic(0)

    val size: Int = _size.value

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E {
        require(index < size)
        return array[index].value as E
    }
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME
*/
/*package mpp.dynamicarray

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


/*
class DynamicArrayImpl<E> : DynamicArray<E> {
    private val core = atomic(Core<E>(INITIAL_CAPACITY))
    private val usingFlag = atomic(0)

    override fun get(index: Int): E = core.value.get(index)

    override fun put(index: Int, element: E) {
        while (true) {
            val curCore = core.value
            if (index < curCore._size.value) {
                val curNode = curCore.getArray()[index].value
                val newNode = Node(element)
                if (!curNode!!.isRemoved && curCore.getArray()[index].compareAndSet(curNode, newNode))
                    return
            } else
                throw IllegalArgumentException()
        }
    }

    override fun pushBack(element: E) {
        while (true) {
            val curCore = core.value
            val pos = curCore._size.value
            val newNode = Node(element)
            if (pos >= curCore._capacity) {
                if (!usingFlag.compareAndSet(expect = 0, update = 1))
                    continue
                val newCore = Core<E>(curCore._capacity * 2, pos)
                var i = 0
                while (i != pos) {
                    val curNode = curCore.getArray()[i].value
                    val newRemovedNode = RemovedNode(curNode!!.value)
                    if (!curCore.getArray()[i].compareAndSet(curNode, newRemovedNode))
                        continue
                    newCore.getArray()[i++].value = curNode
                }
                core.compareAndSet(curCore, newCore)
                usingFlag.value = 0
            } else if (curCore.getArray()[pos].compareAndSet(null, newNode) && curCore._size.compareAndSet(pos, pos + 1))
                return
        }
    }

    override val size: Int get() = core.value._size.value
}

open class Node<E> (v: E) {
    open val isRemoved = false
    val value = v
}

class RemovedNode<E>(v: E) : Node<E>(v) {
    override val isRemoved = true
}

private class Core<E>(
    capacity: Int,
    sz: Int = 0
) {
    private val array = atomicArrayOfNulls<Node<E>>(capacity)
    val _capacity = capacity
    val _size = atomic(sz)

    val size: Int = _size.value

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E {
        require(index < size)
        if (array[index].value == null)
            throw IllegalArgumentException()
        return array[index].value!!.value
    }

    fun getArray(): AtomicArray<Node<E>?> {
        return array
    }


}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME
*/

class DynamicArrayImpl<E> : DynamicArray<E> {
    private val usingFlag = atomic(0)
    private val core = atomic(Core<E>(INITIAL_CAPACITY, INITIAL_SIZE))

    override fun get(index: Int): E {
        if (index < core.value.size.value)
            return core.value.getArray()[index].value!!.value
        else
            throw IllegalArgumentException()
    }

    override fun put(index: Int, element: E) {
        while (true) {
            val curCore = core.value
            if (index < curCore.size.value) {
                val curNode = curCore.getArray()[index].value
                val newNode = Node(element)
                if (!curNode!!.isRemoved && curCore.getArray()[index].compareAndSet(curNode, newNode))
                    return
            } else
                throw IllegalArgumentException()
        }
    }

    override fun pushBack(element: E) {
        while (true) {
            val curCore = core.value
            val pos = curCore.size.value
            val newNode = Node(element)
            if (pos >= curCore.capacity) {
                if (!usingFlag.compareAndSet(expect = 0, update = 1))
                    continue
                val newCore = Core<E>(curCore.capacity * 2, pos)
                var i = 0
                while (i != pos) {
                    val curNode = curCore.getArray()[i].value
                    val newRemovedNode = RemovedNode(curNode!!.value)
                    if (!curCore.getArray()[i].compareAndSet(curNode, newRemovedNode))
                        continue
                    newCore.getArray()[i++].value = curNode
                }
                core.compareAndSet(curCore, newCore)
                usingFlag.value = 0
            } else if (curCore.getArray()[pos].compareAndSet(null, newNode) && curCore.size.compareAndSet(pos, pos + 1))
                return
        }
    }

    override val size: Int get() {
        return core.value.size.value
    }
}

open class Node<E> (v: E) {
    open val isRemoved = false
    val value = v
}

class RemovedNode<E>(v: E) : Node<E>(v) {
    override val isRemoved = true
}

private class Core<E> constructor(c: Int, s: Int) {
    private val array = atomicArrayOfNulls<Node<E>>(c)
    val size = atomic(s)
    val capacity = c

    fun getArray(): AtomicArray<Node<E>?> {
        return array
    }
}

private const val INITIAL_CAPACITY = 1
private const val INITIAL_SIZE = 0


*/