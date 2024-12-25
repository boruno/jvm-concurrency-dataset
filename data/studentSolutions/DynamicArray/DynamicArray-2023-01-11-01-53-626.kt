//package mpp.dynamicarray

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
    private val nextCore = atomic(Core<E>(INITIAL_CAPACITY))

    override fun get(index: Int): E = core.value.get(index)

    override fun put(index: Int, element: E) {
        while (true){
            var curCore = core.value
            val curNextCore = nextCore.value

            if (curNextCore.cap == curCore.cap * 2){
                curCore = curNextCore
            }
            if (curCore.put(index, element)){
                return
            }
        }
    }

    override fun pushBack(element: E) {
        while (true){
            val curNextCore = nextCore.value
            var curCore = core.value
            if (curCore.size < curCore.cap){
                if (!curCore.pushBack(element)){
                    if (curCore.size > curCore.cap){
                        continue
                    }
                    put(curCore.cap - 1, element)
                    break
                } else {
                    break
                }
            } else{
                if (curNextCore.cap == curCore.cap * 2 && curNextCore.size >= curNextCore.cap){
                    curCore = curNextCore
                }
                val newCore = Core<E>(curCore.cap * 2)
                if (nextCore.compareAndSet(curNextCore, newCore)){
                    for (i in 0 until curCore.cap){
                        var temp: E? = curCore.cancelUpdate(i)
                        if (!newCore.pushBack(temp)){
                            if (temp != null){
                                put(i, temp!!)
                            }
                        }
                    }
                    if (!newCore.pushBack(element)){
                        put(curCore.cap, element)
                    }
                    if (core.compareAndSet(curCore, newCore)){
                        break
                    }
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
        try {
            if (array[index].value == null){
                throw IllegalArgumentException()
            }
        } catch (e: ArrayIndexOutOfBoundsException){
            throw IllegalArgumentException()
        }
        return array[index].value!!.v as E
    }

    fun put(index: Int, element: E): Boolean {
//        require(index < size)
        try {
            while (true){
                val nowElement = array[index].value
                if (array[index].value is FixedValue<E>){
                    return false
                }
                if (array[index].compareAndSet(nowElement, CasualValue(element))){
                    return true
                }
            }
        } catch (e: ArrayIndexOutOfBoundsException){
            throw IllegalArgumentException()
        }
    }

    fun pushBack(element: E?): Boolean {
        val index = _size.getAndIncrement()
        if (index >= cap){
            return false
        }
        while (true){
//            val nowElement = array[index].value //size is size - 1 + 1 element? so it's new one
            if (array[index].compareAndSet(null, CasualValue(element))){
                return true
            }
            return false
        }
    }

    fun cancelUpdate(index: Int): E?{
        while (true) {
            val temp = array[index].value
            val cell: Value<E> = if (temp == null) {
                MovedValue(null)
            } else {
                FixedValue(temp.v)
            }
            if (array[index].compareAndSet(temp, cell)) {
                if (temp != null) {
                    return temp.v
                }
                return null
            }
        }
    }
}

abstract class Value<E>(v: E?){
    val v: E? = v
}

class CasualValue<E>(v: E?) : Value<E>(v) {
}

class MovedValue<E>(v: E?) : Value<E>(v) {
}

class FixedValue<E>(v: E?) : Value<E>(v) {
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME

fun main(){
    test1()
}

fun test2(){
    val d = DynamicArrayImpl<Int>()
    println(d.size)
    d.pushBack(4)
    println(d.size)
    d.pushBack(3)
    d.pushBack(3)
    d.pushBack(1)
    d.pushBack(2)
    d.pushBack(3)
    println(d.get(5))

//    println(d.size)
}

fun test1(){
    val d = DynamicArrayImpl<Int>()
    d.pushBack(4)
    d.pushBack(3)
    val onFinish = Phaser(1 + 1)
    Thread{
        d.pushBack(1)
        d.pushBack(1)
        println(d.get(5))
        onFinish.arrive()
    }.start()
    Thread{
        d.pushBack(3)
        d.pushBack(1)
        d.pushBack(4)
        onFinish.arrive()
    }.start()
    onFinish.arriveAndAwaitAdvance()
}