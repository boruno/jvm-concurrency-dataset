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
        core.value.put(index, element)
    }

    override fun pushBack(element: E) {
        while (true){
            if (core.value.size < core.value.cap){
                core.value.pushBack(element)
                break
            } else{
                val curCore = core
                val newCore = Core<E>(core.value.cap * 2)
                for (i in 0 until core.value.size){
                    val temp = core.value.get(i)
                    newCore.pushBack(temp)
                }
                newCore.pushBack(element)
                if (core.compareAndSet(curCore.value, newCore)){
                    break
                }
            }

        }
    }

    override val size: Int get() = core.value.size
}

private class Core<E>(
    capacity: Int,
) {
    private val array = atomicArrayOfNulls<E>(capacity)
    private val _size = atomic(0)

    val size: Int
        get() {
            return _size.value
        }
    val cap: Int = capacity

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E {
        require(index < size)
        return array[index].value as E
    }

    fun put(index: Int, element: E) {
        require(index < size)
        while (true){
            val nowElement = array[index].value
            if (array[index].compareAndSet(nowElement, element)){
                return
            }
        }
    }

    fun pushBack(element: E) {
        while (true){
            val nowElement = array[size].value
            if (array[size].compareAndSet(nowElement, element)){
                _size.incrementAndGet()
                return
            }
        }
    }
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME

fun main(){
    val d = DynamicArrayImpl<Int>()
    println(d.pushBack(2))
    println(d.get(0))
    d.pushBack(3)
    d.pushBack(4)
    println(d.get(1))
    println(d.get(2))
}