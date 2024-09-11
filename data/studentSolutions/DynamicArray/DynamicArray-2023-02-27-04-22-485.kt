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
        var curCore = core.value
        while (true) {
            val old = curCore.get(index)
            if(old != null) {
                if (!old.removed){
                    if (curCore.cas(index, old, elem)) {
                        return
                    }
                } else {
                    val nextCore = curCore.next.value
                    curCore = nextCore!!
                }
            } else {
                if (curCore.cas(index, null, elem)){
                    return
                }
            }
        }
    }

    override fun pushBack(element: E) {
        val elem = Elem(element, false)
        while (true) {

            val curLen = size
            val cCore = core.value
            val curSize = core.value.capacity

            if (curLen + 1 > curSize) {
                var newCore = Core<E>(curSize * 2, curLen)
                if (!cCore.next.compareAndSet(null, newCore)) {
                    newCore = cCore.next.value!!
                }
                var flag = false
                for (i in 0 until curSize) {
                    val transferElem = cCore.get(i) ?: continue
                    cCore.cas(i, transferElem, Elem(transferElem.value, true))
                    newCore.cas(i,null, Elem(transferElem.value, false))


                }
                if (!core.compareAndSet(cCore, newCore) || flag){
                    continue
                }
            } else {
                if (core.value.cas(curLen, null, elem)){
                    core.value.incSize(curLen, curLen + 1)
                    return
                } else {
                    core.value.incSize(curLen, curLen + 1)
                }
            }
        }
    }

    override val size: Int get() = core.value.getSize().value
}

private class Elem<E>(val value: E, var removed: Boolean)

private class Core<E>(
    val capacity: Int,
    len: Int = 0
) {
    private val array = atomicArrayOfNulls<Elem<E>>(capacity)
    private val _size = atomic(len)
    val next:AtomicRef<Core<E>?> = atomic(null)

    fun cas(index: Int, expect: Elem<E>?, update:Elem<E>?) : Boolean {
        return array[index].compareAndSet(expect, update)
    }

    fun get(index: Int): Elem<E>? {
        require(index < _size.value)
        return array[index].value
    }

    fun incSize(data: Int, inc : Int){
        _size.compareAndSet(data, inc)
    }

    fun getSize(): AtomicInt {
        return _size
    }
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME