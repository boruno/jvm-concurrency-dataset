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
        val size = curCore.size.value
        require(index < size)
        while(true) {
            curCore.array[index].getAndSet(element)
            if(curCore.next.value == null){
                return
            }
            curCore = curCore.next.value!!
        }

    }

    override fun pushBack(element: E) {
        while (true) {
            val curCore = core.value
            val size = curCore.getSizeCore()
            val capacity = curCore.getCapacityCore()
            if (size < capacity) {
                if (curCore.array[size].compareAndSet(null, element)) {
                    curCore.size.compareAndSet(size, size + 1)
                    return
                }
                curCore.size.compareAndSet(size, size + 1)
            } else {
                val newCore = Core<E>(capacity * 2)
                newCore.updateSize(size)
                curCore.updateNext(newCore)
                for (i in 0 until capacity) {
                    newCore.array[i].compareAndSet(null, curCore.get(i))
                }
                this.core.compareAndSet(curCore, newCore)
            }
        }
    }

    override val size: Int get() = core.value.getSizeCore()
}

private class Core<E>(
    val capacity: Int,
) {
    val array = atomicArrayOfNulls<E>(capacity)
    val size = atomic(0)
    val next : AtomicRef<Core<E>?> = atomic(null)

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E {
        require(index < size.value)
        return array[index].value as E
    }
    fun updateNext(newNext: Core<E>?) {
        next.compareAndSet(null, newNext)
    }

//    fun getNext(): AtomicRef<Core<E>?>{
//        return next
//    }
    fun updateSize(newSize: Int) {
        size.compareAndSet(0, newSize)
    }
    fun getSizeCore(): Int{
        return size.value
    }
    fun getCapacityCore(): Int{
        return capacity
    }
    fun addElementInArray(index: Int, element: E){
        array[index].value = element
    }
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME