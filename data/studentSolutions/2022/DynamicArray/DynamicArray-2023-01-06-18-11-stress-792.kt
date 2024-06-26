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



class Core<E>(
    private val _capacity: Int,
    length: Int = 0
) {
    private val array = atomicArrayOfNulls<E>(_capacity)
    private val _size = atomic(length)
    val next: AtomicRef<Core<E>?> = atomic(null)

    val size: Int = _size.value
    val capacity: Int = _capacity

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E {
        require(index < size)
        return array[index].value as E
    }

    fun set(index: Int, value: E){
        require(index < size)
        array[index].getAndSet(value)
    }

    fun cas(index: Int, expect: E?, update: E?) : Boolean {
        return array[index].compareAndSet(expect, update)
    }

    fun sizeCas(expect: Int, update: Int) : Boolean {
        return _size.compareAndSet(expect, update)
    }
}


/*class Node<T>(private val capacity: Int, length: Int, next: Node<T>?) {
    val array: AtomicArray<T?> = atomicArrayOfNulls<T>(capacity)
    val len: AtomicInt = atomic(length)
    val next: AtomicRef<Node<T>?> = atomic(next)

    fun getCapacity(): Int {
        return capacity
    }
}*/

const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME