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
    private val core = atomic(Core<E>(INITIAL_CAPACITY))
    override fun get(index: Int): E = core.value.get(index)

    override fun put(index: Int, element: E) {
        var curCore = core.value
        val size = curCore.getSizeCore()
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
            if (size.value < capacity) {
                if (curCore.compareAndSetInArray(curCore.getSizeCore(), element)) {
                    curCore.increment()
                    return
                }
                size.getAndIncrement()
            } else {
                val newCore = Core<E>(capacity * 2)
                newCore.updateSize(curCore.getSizeCore())
                curCore.updateNext(newCore)
                for (i in 0 until capacity) {
                    newCore.compareAndSetInArray(i, curCore.get(i))
                }
                core.compareAndSet(curCore, newCore)
            }
        }
    }

    override val size: Int get() = core.value.getSizeCore()
}

private class Core<E>(
    capacity: Int,
) {
    private val array = atomicArrayOfNulls<E>(capacity)
    private val _capacity = atomic(capacity)
    private val _size = atomic(0)
    private val next : AtomicRef<Core<E>?> = atomic(null)

    val size = atomic(0)

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E {
        require(index < getSizeCore())
        return array[index].value as E
    }
    fun updateNext(newNext: Core<E>?) {
        next.compareAndSet(null, newNext)
    }

    fun getNext(): AtomicRef<Core<E>?>{
        return next
    }
    fun updateSize(newsize: Int) {
        _size.getAndSet(newsize)
    }
    fun getSizeCore(): Int{
        return _size.value
    }

    fun increment() {
        _size.incrementAndGet()
    }

    fun getCapacity(): Int{
        return _capacity.value
    }
    fun addElementInArray(index: Int, element: E){
        array[index].value = element
    }
    fun compareAndSetInArray(index: Int, element: E): Boolean{
        return array[index].compareAndSet(null, element)
    }
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME