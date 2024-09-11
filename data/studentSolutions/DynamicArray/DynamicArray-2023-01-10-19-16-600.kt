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
        while (true){
            val curCore = core.value
            if (curCore.put(index, element)){
                return
            }
        }
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
                    var temp: E? = curCore.cancelUpdate(i)
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
    private val array = atomicArrayOfNulls<Value<E>>(capacity)
    private val _size = atomic(0)

    val size: Int
        get() {
            return _size.value
        }
    val cap: Int = capacity

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E {
        require(index < size)
        return array[index].value!!.v as E
    }

    fun put(index: Int, element: E): Boolean {
        require(index < size)
        while (true){
            val nowElement = array[index].value
            if (array[index].value is FixedValue<E>){
                return false
            }
            if (array[index].compareAndSet(nowElement, CasualValue(element))){
                return true
            }
        }
    }

    fun pushBack(element: E?): Boolean {
        if (size + 1 > cap){
            return false
        }
        val index = _size.getAndIncrement() //size = size - 1 + 1 element, so it's new one
        while (true){
            val nowElement = array[index].value
            if (array[index].compareAndSet(nowElement, CasualValue(element))){
                return true
            }
        }
    }

    fun cancelUpdate(index: Int): E?{
        while (true) {
            val temp = array[index].value ?: return null
            if (array[index].compareAndSet(temp, FixedValue(temp.v))) {
                return temp.v
            }
        }
    }
}

abstract class Value<E>(v: E?){
    val v: E? = v
}

class CasualValue<E>(v: E?) : Value<E>(v) {
}

class FixedValue<E>(v: E?) : Value<E>(v) {
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME

fun main(){
    test2()
}

fun test2(){
    val d = DynamicArrayImpl<Int>()
    println(d.size)
    d.pushBack(5)
    println(d.get(0))
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