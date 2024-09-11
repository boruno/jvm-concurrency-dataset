package mpp.dynamicarray

import kotlinx.atomicfu.*
import javax.lang.model.type.NullType
import kotlin.reflect.typeOf

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

    override fun get(index: Int): E {
        if (index < 0 || index >= size) {
            throw IllegalArgumentException("Index out of bounds")
        }
        return core.value.get(index)?.value!!
    }

    override fun put(index: Int, element: E) {
        if (index < 0 || index >= size) {
            throw IllegalArgumentException("Index out of bounds")
        }
        val elem = Elem<E>(element, false)
        while (true) {
            val old = core.value.get(index)
            if(old != null) {
                if (!old.removed && core.value.cas(index, old, elem)) {
                    return
                }
            } else {
                if (core.value.cas(index, null, elem)){
                    return
                }
            }
        }
    }

    override fun pushBack(element: E) {
        val elem = Elem(element, false)
        while (true) {
            val curSize = core.value.capacity
            val curLen = core.value.size

            val cCore = core.value
            if (curLen + 1 > curSize) {
                val newCore = Core<E>(curSize * 2)
                for (i in 0 until curSize) {
                    val transferElem = cCore.get(i)
                    cCore.cas(i, transferElem, Elem(transferElem?.value!!, true))
                    newCore.cas(i,null, Elem(transferElem.value, false))
                }
                if (!core.compareAndSet(cCore, newCore)){
                    continue
                }
            } else {
                if (core.value.cas(curLen, null, elem)){
                    core.value.incSize()
                    return
                }
            }
        }
    }

    override val size: Int get() = core.value.size
}

private class Elem<E>(val value: E, var removed: Boolean)

private class Core<E>(
    val capacity: Int,
) {
    private val array = atomicArrayOfNulls<Elem<E>>(capacity)
    private val _size = atomic(0)

    var size: Int = _size.value

    fun cas(index: Int, expect: Elem<E>?, update:Elem<E>?) : Boolean {
        return array[index].compareAndSet(expect, update)
    }

    fun get(index: Int): Elem<E>? {
        require(index < size)
        return array[index].value
    }

    fun incSize(){
        size = _size.incrementAndGet()
    }
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME