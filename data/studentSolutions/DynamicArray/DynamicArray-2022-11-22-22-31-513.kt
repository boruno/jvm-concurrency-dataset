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
    private val core: AtomicRef<Core<E>?> = atomic(Core(INITIAL_CAPACITY))
    private val newArray: AtomicRef<Core<E>?> = atomic(null)

    override fun get(index: Int): E = core.value!!.get(index)

    override fun put(index: Int, element: E) {
        var currentCore = core.value
        var currentValue = currentCore!!.get(index)
        var currentNewArray = newArray.value
        if (currentValue != null)
        {
            while(true)
            {
                if(currentCore!!.putCas(index, element,  currentValue ))
                    return
                if(currentNewArray != null) {
                    if (currentNewArray.putCas(index,  element, currentValue))
                        return
                }
                currentCore = core.value
                currentValue = currentCore!!.get(index)
                currentNewArray = newArray.value
            }
        }
        else
        {
            while(true)
            {
                if (currentCore!!.putCas(index, element, null))
                    return
                currentCore = core.value
            }
        }
    }

    override fun pushBack(element: E) {
        while(true)
        {
            if (newArray.compareAndSet(null, Core(size + 1)))
            {
                val oldCore = core.value
                val currentNew = newArray.value
                for (i in size - 1 downTo 0)
                {
                    val transferredValue = core.value!!.get(i)
                    if (transferredValue != null) {
                        newArray.value!!.put(i, transferredValue)
                        core.value!!.put(i, null)
                    }
                }
                newArray.value!!.put(size, element)
                if(!core.compareAndSet(oldCore, newArray.value))
                    throw Exception("Cas failed where not supposed")
                if(!newArray.compareAndSet(currentNew, null))
                    throw Exception("Cas failed where not supposed")
                return
            }
        }
    }

    override val size: Int get() = core.value!!.size
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

    fun putCas(index: Int, value: E?, expect: E?): Boolean {
        require(index < size)
        return array[index].compareAndSet(expect, value)
    }

    fun put(index: Int, value: E?) {
        require(index < size)
        val oldValue = array[index].value
        if(!array[index].compareAndSet(oldValue, value))
            throw Exception("CAS failed in CORE")
    }
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME