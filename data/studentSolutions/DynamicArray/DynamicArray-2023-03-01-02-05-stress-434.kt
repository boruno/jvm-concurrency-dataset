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
        val size = curCore._size.value
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
        val index = core.value._size
        while (true) {
            val curCore = core.value
            val size = curCore._size.value
            val capacity = curCore.capacity
            if (size < capacity) { // && index.value < capacity) {
                if (curCore.array[size].compareAndSet(null, element)) {
                    curCore._size.compareAndSet(size, size + 1)
                    return
                }
                //index.compareAndSet(index.value, index.value + 1)
                curCore._size.compareAndSet(size, size + 1)
            } else {
                val newCore = Core<E>(capacity * 2)
                newCore.updateSize(size)
                curCore.updateNext(newCore)
                for (i in 0 until capacity) {
                    newCore.array[i].compareAndSet(null, curCore.get(i))
                }
                this.core.compareAndSet(curCore, curCore.next.value!!)
            }
        }
    }

    override val size: Int get() = core.value.getSizeCore()
}

private class Core<E>(
    val capacity: Int,
) {
    val array = atomicArrayOfNulls<E>(capacity)
    val _size = atomic(0)
    val helpSize = atomic(0)
    val next : AtomicRef<Core<E>?> = atomic(null)

    val size: Int = _size.value

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E {
        require(index < _size.value)
        return array[index].value as E
    }
    fun updateNext(newNext: Core<E>?) {
        next.compareAndSet(null, newNext)
    }

//    fun getNext(): AtomicRef<Core<E>?>{
//        return next
//    }
    fun updateSize(newsize: Int) {
        _size.compareAndSet(0, newsize)
    }
    fun getSizeCore(): Int{
        return _size.value
    }

    fun increment() {
        _size.getAndIncrement()
    }

//    fun getCapacity(): Int{
//        return capacity
//    }
    fun addElementInArray(index: Int, element: E){
        array[index].value = element
    }
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME