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
        var curCore = core.value
        val size = curCore.size
        require(index < size)
        while(true) {
            curCore.addElementInArray(index, element)
            if(curCore.getNext().value == null){
                return
            }
            curCore = curCore.getNext().value!!
        }

    }

    override fun pushBack(element: E) {
        while (true) {
            val curCore = core.value
            val size = curCore.size
            val capacity = curCore.getCapacity()
            if (size < capacity) {
                curCore.incrementSize()
                if (curCore.compareAndSetInArray(size, element)) {
                    return
                }
            } else {
                val newCore = Core<E>(capacity * 2)
                curCore.updateNext(newCore)
                newCore.updateSize(size)
                for (i in 0 until size) {
                    newCore.compareAndSetInArray(i, curCore.getElement(i)!!)
                }
                core.compareAndSet(curCore, newCore)
            }
        }
    }

    override val size: Int get() = core.value.size
}

private class Core<E>(
    private val capacity: Int,
) {
    private val array = atomicArrayOfNulls<E>(capacity)
    private val _size = atomic(0)
    private val next : AtomicRef<Core<E>?> = atomic(null)

    val size: Int = _size.value

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E {
        require(index < size)
        return array[index].value as E
    }
    fun updateNext(newNext: Core<E>?) {
        next.compareAndSet(null, newNext)
    }

    fun getNext(): AtomicRef<Core<E>?>{
        return next
    }

    fun incrementSize(){
        _size.getAndIncrement()
    }

    fun updateSize(size: Int) {
        _size.getAndSet(size)
    }

    fun getCapacity(): Int{
        return capacity
    }
    fun addElementInArray(index: Int, element: E){
        array[index].value = element
    }
    fun compareAndSetInArray(index: Int, element: E): Boolean{
        return array[index].compareAndSet(null, element)
    }

    fun getElement(index: Int): E? {
        return array[index].value
    }
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME