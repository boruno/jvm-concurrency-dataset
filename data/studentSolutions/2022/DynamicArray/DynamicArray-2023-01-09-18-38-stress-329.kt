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
    private val flag = atomic(0)

    override fun get(index: Int): E{
        while (true){
            if (size >= index || flag.value == 0){
                return core.value.get(index)
            }
        }
    }

    override fun put(index: Int, element: E) {
        while (true){
            if (size >= index || flag.value == 0){
                if (core.value.put(index, element)){
                    return
                }
            }
        }
    }

    override fun pushBack(element: E) {
        flag.incrementAndGet()
        while (true){
            val lastCore = this.core.value
            val newSize = lastCore.resize()
            val newCore = Core<E>(newSize)
            for (i in 0 until newSize){
                newCore.resize()
                newCore.put(i, get(i))
            }
            newCore.resize()
            newCore.put(newSize, element)
            if (core.compareAndSet(lastCore, newCore)){
                flag.decrementAndGet()
                return
            }
        }
    }


    fun checkSize(index: Int){
        if (index > size){
            throw IllegalArgumentException()
        }
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

    fun put(index: Int, element: E): Boolean{
        return (array[index].compareAndSet(get(index), element))
    }

    fun resize(): Int {
        return _size.incrementAndGet()
    }
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME