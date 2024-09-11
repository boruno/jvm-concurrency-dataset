package mpp.dynamicarray

import kotlinx.atomicfu.*
import java.util.concurrent.Phaser

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
            val curCore = core.value
            if (curCore.size < curCore.cap){
                if (curCore.pushBack(element)){
                    break
                }
            } else{
                val newCore = Core<E>(curCore.cap * 2)
                for (i in 0 until curCore.cap){
                    val temp = curCore.get(i)
                    newCore.pushBack(temp)
                }
                newCore.pushBack(element)
                if (core.compareAndSet(curCore, newCore)){
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

    fun pushBack(element: E): Boolean {
        val index = _size.getAndIncrement()
        if (index >= cap){
            return false
        }
        while (true){
            val nowElement = array[index].value //size is size - 1 + 1 element? so it's new one
            if (array[index].compareAndSet(nowElement, element)){
                return true
            }
        }
    }
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME

fun main(){
    test2()
}

fun test2(){
    val d = DynamicArrayImpl<Int>()
    println(d.size)
    d.pushBack(5)
    println(d.size)
    d.pushBack(2)
    println(d.size)
}

fun test1(){
    val d = DynamicArrayImpl<Int>()
    val onFinish = Phaser(1 + 1)
    Thread{
        d.pushBack(5)
        onFinish.arrive()
    }.start()
    Thread{
        d.pushBack(5)
        onFinish.arrive()
    }.start()
    onFinish.arriveAndAwaitAdvance()
}