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
        if(core.value.checkForResize())
            resizeCore()
        core.value.put(index,element)
    }

    override fun pushBack(element: E) {
        if(core.value.checkForResize())
            resizeCore()
        core.value.pushBack(element)
    }

    fun resizeCore(){
        val curr_core = core.value
        val new_core = Core<E>(size*2)
        for(i in 0 until size)
        {
            new_core.put(i,core.value.get(i))
        }
        new_core.set_size(size)
        core.compareAndSet(curr_core, new_core)
    }

    override val size: Int get() = core.value.size
}

private class Core<E>(
    capacity: Int,
) {
    private val array = atomicArrayOfNulls<E>(capacity)
    private val _size = atomic(0)

    var size: Int = _size.value

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E {
        require(index < size)
        return array[index].value as E
    }

    fun checkForResize(): Boolean{
        return size == array.size
    }

    fun pushBack(element: E) {
        while (true) {
            if(array[size].compareAndSet(null, element)) {
                _size.getAndIncrement()
                size = _size.value
                return
            }
        }
    }

    fun put(index: Int, element: E){
        while(true){
            val arr_val = array[index].value
            if(arr_val == null){
                val curr_size = size
                if(!_size.compareAndSet(curr_size,index))
                    continue
                else
                    size = _size.value
            }
            if(array[index].compareAndSet(arr_val,element))
            {
                return
            }
        }
    }

    fun set_size(newSize: Int){
        _size.compareAndSet(0, newSize)
    }
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME
