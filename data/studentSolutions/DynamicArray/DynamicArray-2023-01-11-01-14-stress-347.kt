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
        if(index >= core.value.get_capacity())
            return
        core.value.put(index,element)
    }

    override fun pushBack(element: E) {
        if(core.value.checkForResize())
            resizeCore()
        core.value.pushBack(element)
        return
    }

    fun resizeCore(){
        val curr_core = core.value
        val old_size = size
        val new_core = Core<E>(old_size+1)
        for(i in 0 until old_size)
        {
            if(!new_core.put_copy(i,core.value.get(i)))
                return
        }
        new_core.set_size(old_size)
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
            val curr_size = _size.value
            if(array[curr_size].compareAndSet(null, element)) {
                _size.getAndIncrement()
                size = _size.value
                return
            }
        }
    }

    fun put(index: Int, element: E){
        while(true){
            if(_size.value == 0)
            {
                _size.getAndIncrement()
                size = _size.value
            }
            val arr_val = array[index].value
            if(array[index].compareAndSet(arr_val,element))
            {
                return
            }
        }
    }

    fun set_size(newSize: Int){
        _size.compareAndSet(0, newSize)
    }

    fun put_copy(index: Int, element: E): Boolean{
        return array[index].compareAndSet(null,element)
    }

    fun get_capacity(): Int{
        return array.size
    }
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME
