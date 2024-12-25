//package mpp.dynamicarray

import kotlinx.atomicfu.*
import kotlin.math.max

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
    val _size = atomic(0)

    override fun get(index: Int): E {
        require(index in 0 until size)
        while (true) {
            val el = core.value.array[index].value
            if (el is RemovedNode) {
                helpCopy()
            } else if (el is Node) {
                return el.value!!
            } else if (el == null) {
                throw IllegalStateException("Жопа")
            } else {
                throw IllegalStateException("Не может быть ")
            }
        }
    }

    private fun helpCopy() {
        val curCore = core.value
        if (curCore.size > _size.value) return
        // Если мы тут, значит:
        // 1) Идет фаза копирования из array -> next
        // 2) Фаза закончилась и в array лежат Move
        curCore.nextCore.compareAndSet(null, Core(curCore.size * 2))
        var index = -1
        while (++index < curCore.array.size) {
            var elFromNext = curCore.nextCore.value!!.array[index].value
            var el = curCore.array[index].value
            while (el is Node) {
                //
                if (curCore.nextCore.value!!.array[index].compareAndSet(elFromNext, el))
                    if (curCore.array[index].compareAndSet(el, RemovedNode()))
                        break
                elFromNext = curCore.nextCore.value!!.array[index].value
                el = curCore.array[index].value
            }
        }
        core.compareAndSet(curCore, curCore.nextCore.value!!)
    }

    override fun put(index: Int, element: E) {
        require(index in 0 until size)
        while (true) {
            val el = core.value.array[index].value ?: break
            if (el is RemovedNode) {
                helpCopy()
                continue
            }
            if (core.value.array[index].compareAndSet(el, Node(element)))
                return
        }
        throw IllegalStateException()
    }

    override fun pushBack(element: E) {
        while (true) {
            val curSize = _size.value
            val curCore = core.value
            if (curSize < curCore.size) {
                if (core.value.array[curSize].compareAndSet(null, Node(element))) {
                    // interrupt и сразу не lock-free, надо форсить
                    _size.compareAndSet(curSize, curSize + 1)
                    return
                } else {
                    _size.compareAndSet(curSize, curSize + 1)
                }
            } else {
                helpCopy()
            }
        }
    }

    private fun checkIndex(index: Int) {
        if (index < 0 || index >= _size.value)
            throw IllegalArgumentException()
    }

    override val size: Int
        get() {
            return _size.value
        }

    private class Core<E>(
        val size: Int
    ) {
        val array: AtomicArray<Node<E>?> = atomicArrayOfNulls(size)
        val nextCore: AtomicRef<Core<E>?> = atomic(null)
    }
}

open class Node<E> (val value: E?)

class RemovedNode<E> : Node<E>(null)

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME
*/

class DynamicArrayImpl<E> : DynamicArray<E> {
    private val _size = atomic(0)
    private val core = atomic(Core<E>(INITIAL_CAPACITY))

    /// MODIFIED
    override fun get(index: Int): E {
        require(index in 0 until size)
        while (true) {
            val curCore = core.value
            val node = curCore.array[index].value ?: break
            if (node is RemovedNode) {
                ensureCapacity()
                continue
            }
            return node.value!!
        }
        throw IllegalStateException()
    }
    ///

    private fun ensureCapacity() {
        val curCore = core.value
        if (!predicate(curCore.capacity)) return
        val tmpCore: Core<E> = Core(curCore.capacity * max(2, size / curCore.capacity + 1))
        curCore.newCore.compareAndSet(null, tmpCore)
        (0 until curCore.array.size).forEach {
            var elFromNext = curCore.newCore.value!!.array[it].value
            var el = curCore.array[it].value
            while (el is ActiveNode) {
                if (curCore.newCore.value!!.array[it].compareAndSet(elFromNext, el))
                    if (curCore.array[it].compareAndSet(el, RemovedNode()))
                        break
                elFromNext = curCore.newCore.value!!.array[it].value
                el = curCore.array[it].value
            }
        }
        core.compareAndSet(curCore, curCore.newCore.value!!)

        /*
        val curCore = core.value
        if (curCore.capacity > size) return
        val tmpCore: Core<E> = Core(curCore.capacity * max(2, size / curCore.capacity + 1))
        curCore.newCore.compareAndSet(null, tmpCore)
        (0 until curCore.array.size).forEach {
            var curNode = curCore.array[it].value
            var newNode = curCore.newCore.value!!.array[it].value
            while (true) {
                if (curNode is RemovedNode) break
                if (!curCore.newCore.value!!.array[it].compareAndSet(newNode, curNode)) {
                    curNode = curCore.array[it].value
                    newNode = curCore.newCore.value!!.array[it].value
                    continue
                }
                curCore.array[it].compareAndSet(curNode, RemovedNode())
            }
        }
        core.compareAndSet(curCore, curCore.newCore.value!!)
         */
    }

    /// MODIFIED
    override fun put(index: Int, element: E) {
        require(index in 0 until size)
        while (true) {
            val node = core.value.array[index].value ?: break
            if (node is RemovedNode) {
                ensureCapacity()
                continue
            }
            if (core.value.array[index].compareAndSet(node, ActiveNode(element)))
                return
        }
        throw IllegalStateException()
    }
    ///

    /// MODIFIED
    override fun pushBack(element: E) {
        while (true) {
            val curCore = core.value
            if (predicate(curCore.capacity)) {
                ensureCapacity()
                continue
            }
            var curSize = size
            val flag = core.value.array[curSize].compareAndSet(null, ActiveNode(element))
            _size.compareAndSet(curSize++, curSize)
            if (!flag) continue
            return
        }
    }
    ///

    private fun predicate(capacity: Int): Boolean {
        return capacity <= size
    }
    override val size: Int
        get() {
            return _size.value
        }
}

private class Core<E>(val capacity: Int) {
    val array: AtomicArray<Node<E>?> = atomicArrayOfNulls(capacity)
    val newCore: AtomicRef<Core<E>?> = atomic(null)
}

open class Node<E>(val value: E?)

class ActiveNode<E>(v: E?) : Node<E>(v)
class RemovedNode<E> : Node<E>(null)

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME


/*
class DynamicArrayImpl<E> : DynamicArray<E> {
    private val core = atomic(Core<E>(INITIAL_CAPACITY))

    private fun helpCopy() {
        val curCore = core.value
        if (curCore.capacity > core.value.size.value) return
        // Если мы тут, значит:
        // 1) Идет фаза копирования из array -> next
        // 2) Фаза закончилась и в array лежат Move
        curCore.nextCore.compareAndSet(null, Core(curCore.capacity * 2))
        var index = -1
        while (++index < curCore.array.size) {
            var elFromNext = curCore.nextCore.value!!.array[index].value
            var el = curCore.array[index].value
            while (el is Node) {
                //
                if (curCore.nextCore.value!!.array[index].compareAndSet(elFromNext, el))
                    if (curCore.array[index].compareAndSet(el, RemovedNode(null)))
                        break
                elFromNext = curCore.nextCore.value!!.array[index].value
                el = curCore.array[index].value
            }
        }
        core.compareAndSet(curCore, curCore.nextCore.value!!)
    }

    override fun get(index: Int): E {
        require(index in 0 until size)
        val curCore = core.value
        while (true) {
            val element = curCore.array[index].value ?: break
            if (element is RemovedNode) {
                helpCopy()
                continue
            }
            return element.value!!
        }
        throw IllegalStateException()
    }

    override fun put(index: Int, element: E) {
        require(index in 0 until size)
        while (true) {
            val el = core.value.array[index].value
            if (el is RemovedNode) {
                helpCopy()
            } else if (el is Node) {
                if (core.value.array[index].compareAndSet(el, Node(element)))
                    return
            } else if (el == null) {
                throw IllegalStateException("Жопа")
            } else {
                throw IllegalStateException("Не может быть ")
            }
        }
    }

    override fun pushBack(element: E) {
        /*while (true) {
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
        }*/
        while (true) {
            val curSize = core.value.size.value
            val curCore = core.value
            if (curSize < curCore.capacity) {
                if (core.value.array[curSize].compareAndSet(null, Node(element))) {
                    // interrupt и сразу не lock-free, надо форсить
                    core.value.size.compareAndSet(curSize, curSize + 1)
                    return
                } else {
                    core.value.size.compareAndSet(curSize, curSize + 1)
                }
            } else {
                helpCopy()
            }
        }
    }

    override val size: Int get() = core.value.size.value
}

private class Core<E>(
    val capacity: Int,
) {
    val array = atomicArrayOfNulls<Node<E>>(capacity)
    val size = atomic(0)
    val nextCore: AtomicRef<Core<E>?> = atomic(null)

    /*@Suppress("UNCHECKED_CAST")
    fun get(index: Int): E {
        return array[index].value as E
    }*/
}

open class Node<E> (val value: E?)

class RemovedNode<E>(v: E?) : Node<E>(v)

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME
*/


/*
class DynamicArrayImpl<E> : DynamicArray<E> {
    private val core = atomic(Core<E>(INITIAL_CAPACITY))
    private val _size = atomic(0)

    override fun get(index: Int): E {
        if (index < 0 || index >= _size.value) {
            throw IllegalArgumentException()
        }
        while (true) {
            val cell = core.value.get(index)
            when (cell?.hasNotElement) {
                true -> move()
                else -> return cell?.element!!
            }
        }
    }

    override fun put(index: Int, element: E) {
        if (index < 0 || index >= _size.value) {
            throw IllegalArgumentException()
        }
        while (true) {
            val cell = core.value.get(index)
            when (cell?.hasNotElement) {
                true -> move()
                else -> {
                    when (core.value.cas(index, cell, ElementCell(element))) {
                        true -> return
                        false -> continue
                    }
                }
            }
        }
    }

    override fun pushBack(element: E) {
        while (true) {
            val curSize = _size.value
            val curCore = core.value
            when (curSize < curCore.size) {
                true -> {
                    val success = core.value.cas(curSize, null, ElementCell(element))
                    _size.compareAndSet(curSize, curSize + 1)
                    if (success) return
                }
                false -> move()
            }
        }
    }

    private fun move() {
        val curCore = core.value
        if (curCore.size <= _size.value) {
            curCore.next.compareAndSet(null, Core(curCore.size * 2))
            for (i in 0 until curCore.array.size){
                var nextCell = curCore.next.value!!.get(i)
                var cell = curCore.get(i)!!
                while (cell.element != null && !(curCore.next.value!!.cas(i, nextCell, cell) && curCore.cas(i, cell, ElementCell()))) {
                    nextCell = curCore.next.value!!.get(i)
                    cell = curCore.get(i)!!
                }
            }
            core.compareAndSet(curCore, curCore.next.value!!)
        }
    }

    override val size: Int get() = _size.value
}

private class ElementCell<E> {
    var element: E? = null
    var hasNotElement: Boolean? = null

    constructor() {
        hasNotElement = true
        element = null
    }

    constructor(element: E) {
        this.element = element
        hasNotElement = false
    }
}


private class Core<E>(
    capacity: Int,
) {
    val array = atomicArrayOfNulls<ElementCell<E>?>(capacity)
    val next: AtomicRef<Core<E>?> = atomic(null)
    private val _size = atomic(capacity)

    val size: Int = _size.value

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): ElementCell<E>? {
        require(index < size)
        return array[index].value as ElementCell<E>?
    }

    fun cas(index: Int, expected: ElementCell<E>?, update: ElementCell<E>?): Boolean {
        require(index < size)
        return array[index].compareAndSet(expected, update)
    }
}




private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME
*/
