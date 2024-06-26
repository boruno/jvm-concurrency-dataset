package mpp.dynamicarray

import kotlinx.atomicfu.*
import java.lang.Exception

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
    private val core: AtomicRef<Core<Node<E>>> = atomic(Core(INITIAL_CAPACITY, 0))

    override fun get(index: Int): E {
        require(index < core.value.size)
        var currentCore = core.value
        while(true)
        {
            val currentValue = currentCore.get(index)
            if (currentValue!!.getType() == "Node" || currentValue.getType() == "NodeFixed")
                return currentValue.value!!
            if (currentValue.getType() == "NodeMoved")
                currentCore = currentCore.nextList.value!!
        }
    }

    override fun put(index: Int, element: E) {
        require(index < core.value.size)
        var currentCore = core.value
        while(true) {
            val currentValue = currentCore.get(index)
            if (currentValue!!.getType() == "Node") {
                currentCore.putCas(index, currentValue, Node(element))
                return
            }
            if (currentValue.getType() == "NodeFixed" || currentValue.getType() == "NodeMoved>")
                currentCore = currentCore.nextList.value!!
        }
    }

    override fun pushBack(element: E) {
        var needNew = false
        while (true) {
            var currentCore = core.value
            var currentSize = currentCore.size
            if (currentSize < currentCore.capacity && !needNew) {
                var currentIndex = currentSize
                while(currentIndex < currentCore.capacity)
                {
                    if(currentCore.putCas(currentIndex, null, Node(element))) {
                        currentCore.incrementSize()
                        return
                    }
                    else
                    {
                        println(String.format("Current Index: %d", currentIndex))
                        currentIndex += 1
                    }
                }
                needNew = true
            } else {
                while (true) {
                    if (currentCore.nextList.compareAndSet(null, Core(currentCore.capacity * 2, currentCore.capacity + 1))) {
                        val newCore = currentCore.nextList
                        for (i in 0 until currentCore.capacity)
                        {
                            while(true) {
                                val currentElement = currentCore.get(i)
                                if(!currentCore.putCas(i, currentElement, NodeFixed(currentElement!!.value)))
                                    continue
                                val fixedElement = currentCore.get(i)
                                newCore.value!!.putCas(i, null, Node(currentElement.value))
                                currentCore.putCas(i,fixedElement, NodeMoved(fixedElement!!.value))
                                break
                            }
                        }
                        newCore.value!!.putCas(currentSize, null, Node(element))
                        core.compareAndSet(currentCore, currentCore.nextList.value!!)
                        return
                    } else {
                        currentCore = currentCore.nextList.value!!
                        currentSize = currentCore.size
                        if (currentSize < currentCore.capacity) {
                            if (currentCore.putCas(currentSize, null, Node(element)))
                            {
                                currentCore.incrementSize()
                                return
                            }
                        }
                    }
                }
            }
        }
    }

    override val size: Int get() = core.value!!.size
}

private class Core<E>(
    capacity_: Int,
    initialSize: Int
) {
    private val array = atomicArrayOfNulls<E>(capacity_)
    private val _size = atomic(initialSize)
    private val _capacity = atomic(capacity_)

    val size: Int get() = _size.value
    val capacity: Int get() = _capacity.value

    val nextList: AtomicRef<Core<E>?> = atomic(null)

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E? {
        return array[index].value as E
    }

    fun putCas(index: Int, expect: E?, value: E?): Boolean {
        return array[index].compareAndSet(expect, value)
    }

    fun put(index: Int, value: E?) {
        val oldValue = array[index].value
        if (!array[index].compareAndSet(oldValue, value))
            throw Exception("CAS failed in CORE")
    }

    fun incrementSize(): Int {
        if (_size.value >= _capacity.value)
            return _capacity.value
        return _size.getAndIncrement()
    }

    fun putInTheEnd(element: E?) {
        require(size < capacity)
        array[_size.getAndIncrement()].value = element
    }
    fun setSize(newSize: Int): Boolean {
        require(newSize < _capacity.value)
        val currentSize = _size.value
        return _size.compareAndSet(currentSize, newSize)
    }
}

open class Node<E>(value: E?) {
    public val value = value
    open fun getType(): String {
        return "Node"
    }
}
class NodeFixed<E>(value: E?) : Node<E>(value) {
    override fun getType(): String {
        return "NodeFixed"
    }
}
class NodeMoved<E>(value: E?): Node<E>(value) {
    override fun getType(): String {
        return "NodeMoved"
    }
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME