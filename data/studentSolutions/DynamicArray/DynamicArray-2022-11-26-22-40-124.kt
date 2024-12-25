//package mpp.dynamicarray

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
        require(index < size)
        var currentCore = core.value
        var nullptrCounter = 0
        while(true)
        {
            if (index < currentCore.capacity ) {
                val currentValue = currentCore.get(index)
                if (currentValue == null)
                {
                    nullptrCounter++
                    if (currentCore.nextList.value != null)
                        currentCore = currentCore.nextList.value!!
                    if (nullptrCounter > 1000)
                        throw Exception("We are in a deadlock")
                    continue
                }
                if (currentValue!!.getType() == "Node")
                    return currentValue.value!!
                if (currentValue.getType() == "NodeMoved") {
                    currentCore = currentCore.nextList.value!!
                    continue
                }
                if (currentValue.getType() == "NodeFixed")
                {
                    val nextCore = currentCore.nextList.value!!
                    val nextElement = nextCore.get(index)
                    if (nextElement == null)
                        return currentValue.value!!
                    if (nextElement.getType() == "Node")
                        return nextElement.value!!
                    if (nextElement.getType() == "NodeFixed")
                    {
                        currentCore = currentCore.nextList.value!!
                        continue
                    }
                    if (nextElement.getType() == "NodeMoved")
                    {
                        currentCore = currentCore.nextList.value!!
                        continue
                    }
                }
            }
            else
            {
                if (currentCore.nextList.value != null)
                    currentCore = currentCore.nextList.value!!
                else
                    continue
            }
        }
    }

    override fun put(index: Int, element: E) {
        require(index < size)
        var currentCore = core.value
        while(true) {
            if (index >= currentCore.capacity)
            {
                currentCore = currentCore.nextList.value!!
                continue
            }
            val currentValue = currentCore.get(index)
            if (currentValue!!.getType() == "Node") {
                if(currentCore.putCas(index, currentValue, Node(element)))
                    return
                else {
                    currentCore = core.value
                    continue
                }
            }
            if (currentValue.getType() == "NodeMoved") {
                currentCore = currentCore.nextList.value!!
                continue
            }
            if (currentValue.getType() == "NodeFixed")
            {
                val nextCore = currentCore.nextList.value
                val nextElement = nextCore!!.get(index)
                if (nextElement == null)
                {
                    if(nextCore.putCas(index, null, Node(element)))
                    {
                        var oldSize = nextCore.size
                        while(true) {
                            if (oldSize >= index + 1)
                                break
                            if (nextCore.sizeCas(oldSize, index + 1))
                                break
                            oldSize = nextCore.size
                        }
                        if (size < index + 1) {
                            println("Size have not advanced in PUT!");
                            throw Exception("Size have not advanced in PUT!")
                        }
                        currentCore.putCas(index, currentValue, NodeMoved(element))
                        return
                    }
                    continue
                }
                if (nextElement.getType() == "Node")
                {
                    if(nextCore.putCas(index, nextElement, Node(element)))
                        return
                    else
                        continue
                }
                if (nextElement.getType() == "NodeMoved")
                {
                    currentCore.putCas(index, currentValue, NodeMoved(element))
                    continue
                }
                if (nextElement.getType() == "NodeFixed")
                {
                    currentCore = currentCore.nextList.value!!
                    continue
                }
            }
        }
    }

    override fun pushBack(element: E) {
        while(true)
        {
            if(tryPlaceInArray(element))
                return
            tryResizeArray()
        }
    }

    private fun tryPlaceInArray(element: E): Boolean {
        var currentCore: Core<Node<E>>? = core.value
        var currentIndex = size
        while (currentCore != null) {
            if (currentIndex >= currentCore.capacity)
            {
                currentCore = currentCore.nextList.value
                continue
            }
            val currentElement = currentCore.get(currentIndex)
            if (currentElement == null)
            {
                if (currentCore.putCas(currentIndex, null, Node(element)))
                {
                    while(true)
                    {
                        val oldSize = currentCore.size
                        if (oldSize >= currentIndex + 1)
                            break
                        if (currentCore.sizeCas(oldSize, currentIndex + 1))
                            break
                    }
                    if (size < currentIndex + 1) {
                        println(String.format("Size have not advanced in PUSH_BACK!. Our core is current: %d", currentCore === core.value));
                        throw Exception(String.format("Size have not advanced in PUSH_BACK!. Our core is current: %d", currentCore === core.value))
                    }
                    return true
                }
                else
                    continue
            }
            if (currentElement.getType() == "NodeMoved")
            {
                currentCore = currentCore.nextList.value
                continue
            }
            if (currentElement.getType() == "NodeFixed")
            {
                currentIndex++
            }
            if (currentElement.getType() == "Node")
            {
                currentCore = core.value
                currentIndex++
            }

        }
        return false
    }

    private fun tryResizeArray() {
        var currentCore = core.value
        while (true) {
            if (currentCore.nextList.compareAndSet(null, Core(currentCore.capacity * 2, size))) {
                val newCore = currentCore.nextList.value!!
                val currentCapacity = currentCore.capacity
                for (index in 0 until currentCapacity) {
                    while (true) {
                        val currentElement = currentCore.get(index)
                        if (currentElement == null)
                        {
                            var initialCore = core.value
                            while(initialCore != newCore) {
                                if (index >= initialCore.capacity)
                                {
                                    initialCore = initialCore.nextList.value!!
                                    continue
                                }
                                val element = initialCore.get(index)
                                if (element == null)
                                {
                                    if(initialCore.putCas(index, null, NodeMoved(element)))
                                        break
                                    else
                                        continue
                                }
                                if (element.getType() == "Node")
                                {
                                    if(!initialCore.putCas(index, element, NodeFixed(element.value)))
                                        continue;
                                    val fixedElement = initialCore.get(index);
                                    initialCore.nextList.value!!.putCas(index, null, Node(fixedElement!!.value))

                                        var oldSize = initialCore.nextList.value!!.size
                                        while(true) {
                                            if (oldSize >= index + 1)
                                                break
                                            if(initialCore.nextList.value!!.sizeCas(oldSize, index + 1))
                                                break
                                            oldSize = initialCore.nextList.value!!.size
                                        }

                                    initialCore.putCas(index, fixedElement, NodeMoved(fixedElement!!.value))
                                    initialCore = initialCore.nextList.value!!
                                }
                                if (element.getType() == "NodeFixed") {
                                    initialCore.nextList.value!!.putCas(index, null, Node(element!!.value))

                                        var oldSize = initialCore.nextList.value!!.size
                                        while(true) {
                                            if (oldSize >= index + 1)
                                                break
                                            if(initialCore.nextList.value!!.sizeCas(oldSize, index + 1))
                                                break
                                            oldSize = initialCore.nextList.value!!.size
                                        }

                                    initialCore.putCas(index, element, NodeMoved(element!!.value))
                                    initialCore = initialCore.nextList.value!!
                                }
                                if (element.getType() == "NodeMoved") {
                                    initialCore = initialCore.nextList.value!!
                                }
                            }
                            break
                        }
                        if (currentElement!!.getType() == "NodeFixed")
                        {
                            if(newCore.putCas(index, null, Node(currentElement.value)))
                            {
                                var oldSize = newCore.size
                                    while(true) {
                                        if (oldSize >= index + 1)
                                            break
                                        if(newCore.sizeCas(oldSize, index + 1))
                                            break
                                        oldSize = newCore.size
                                    }
                            }
                            currentCore.putCas(index, currentElement, NodeMoved(currentElement.value))
                            break
                        }
                        if (currentElement!!.getType() == "NodeMoved")
                        {
                            // Recheck size
                            break
                        }
                        if (currentElement!!.getType() == "Node")
                        {
                            if (!currentCore.putCas(index, currentElement, NodeFixed(currentElement.value)))
                                continue
                            val fixedElement = currentCore.get(index)
                            newCore.putCas(index, null, Node(fixedElement!!.value))
                                var oldSize = newCore.size
                                while(true) {
                                    if (oldSize >= index + 1)
                                        break
                                    if(newCore.sizeCas(oldSize, index + 1))
                                        break
                                    oldSize = newCore.size
                                }
                            currentCore.putCas(index, fixedElement, NodeMoved(fixedElement!!.value))
                            break
                        }
                    }
                }
                if (currentCore.size > newCore.size)
                {
                    val currentCoreSize = currentCore.size
                    while(true)
                    {
                        val newCoreSize = newCore.size
                        if (newCoreSize >= currentCoreSize)
                            break
                        if (newCore.sizeCas(newCoreSize, currentCoreSize))
                            break
                    }
                }
                while(true) {
                    currentCore = core.value
                    if(core.compareAndSet(currentCore, currentCore.nextList.value!!))
                        break
                }
                    return
            } else {
                if (core.value === currentCore)
                    currentCore = currentCore.nextList.value!!
                else
                    currentCore = core.value
            }
        }
    }

    //override val size: Int get() = core.value.size
    override val size: Int get() {
        var currentCore: Core<Node<E>>? = core.value
        var result = 0
        while(currentCore != null)
        {
            if (currentCore.size > result)
                result = currentCore.size
            currentCore = currentCore.nextList.value
        }
        return result
    }
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

    private fun incrementSize(): Int {
        if (_size.value >= _capacity.value)
            return _capacity.value
        return _size.getAndIncrement()
    }

    fun sizeCas(oldSize: Int, newSize: Int): Boolean {
        return _size.compareAndSet(oldSize, newSize)
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