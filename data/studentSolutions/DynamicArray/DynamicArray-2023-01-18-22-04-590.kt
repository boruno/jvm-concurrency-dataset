//package mpp.dynamicarray

import kotlinx.atomicfu.*
import kotlinx.atomicfu.AtomicRef

class InsertValue <E> {
    private val _value: AtomicRef<E?> = atomic(null)
    private val _write = atomic(false)
    private val _size = atomic(0)
    constructor(value: E?, write: Boolean) {
        _value.compareAndSet(null, value)
        _write.compareAndSet(false, write)
    }
    fun getValue(): E? {
        return _value.value
    }
    fun setWrite(newWrite: Boolean): Boolean {
        val curWrite = _write.value
        return _write.compareAndSet(curWrite, newWrite)
    }
    fun isWriting(): Boolean {
        return _write.value
    }
    fun getSize(): Int {
        return _size.value
    }
}

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
    @Suppress("UNCHECKED_CAST")
    override fun get(index: Int): E {
        if (index >= getRealSize()) {
            throw IllegalArgumentException("Index [$index] '>', than size [$size]")
        }
        if (core.value.array[index].value == null) {
            throw NullPointerException("It's get and there's null pointer")
        }
        return core.value.array[index].value!!.getValue() as E
    }
    override fun put(index: Int, element: E) {
        while (true) {
            val curSize = getRealSize()
            if (index > curSize) {
                throw IllegalArgumentException("Index [$index] '>', than size [$size]")
            }
            val curCore = core.value
            val iV = InsertValue(element, false)
            val curElement = curCore.array[index].value // ToDo:: check
            if (curElement!!.isWriting() || curCore._resize.value) { // Ne atomarno!
                tryResize(curCore)
                continue
            }
            if (!curElement.isWriting() && !curCore._resize.value && curCore.array[index].compareAndSet(curElement, iV)) {
                return
            }
        }
    }

    private fun tryResize(curCore: Core<E>): Int {
        curCore._resize.compareAndSet(expect = false, update = true)
        val newCapacity = curCore._capacity.value * 2
        val newCore = Core<E>(newCapacity)
        var realIndex = 0

        for(i in 0 until curCore._capacity.value) {
            val curElement = curCore.array[i].value ?: continue
            // added null IV block!!!
            curCore.array[i].compareAndSet(curElement, InsertValue(curElement.getValue(), true))
        }

        for (i in 0 until curCore._capacity.value) {
            val curElement = curCore.array[i].value ?: continue
            // added check null and inc real index!!!
            if (curElement.isWriting()) {
                newCore.array[realIndex].getAndSet(InsertValue(curElement.getValue(), false))
                realIndex += 1
            }
            else if (!curElement.isWriting()) {
                val updElement = curCore.array[i].value
                if (curCore.array[i].compareAndSet(updElement, InsertValue(curElement.getValue(), true))) {
                    newCore.array[realIndex].getAndSet(InsertValue(updElement!!.getValue(), false))
                    realIndex += 1
                }
            }
            else if (curCore.array[i].value!!.isWriting()) {
                newCore.array[realIndex].getAndSet(curElement)
                realIndex += 1
            }
        }
        if (core.compareAndSet(curCore, newCore)) {
            return newCapacity
            }
        return -1
    }
    fun getRealSize(): Int {
        var size = 0
        val curCore = core.value
        for (i in 0 until  curCore._capacity.value) {
            if (curCore.array[i].value != null) {
                size += 1
            }
        }
        return size
    }
    override fun pushBack(element: E) {
        while (true) {
            val curCore = core.value
            val curSize = getRealSize()
            if (curSize >= curCore._capacity.value) {
                tryResize(curCore)
            } else {
                val iV = InsertValue(element, false)
                if (curCore.array[curSize].compareAndSet(null, iV)) {
                    return
                }
            }
        }
        // ToDo:: Complete write
    }
    override val size: Int get() = getRealSize()
}


private class Core<E>(capacity: Int) {
    val array = atomicArrayOfNulls<InsertValue<E>>(capacity)
    val _capacity = atomic(capacity)
    val _resize = atomic(false)


    /*@Suppress("UNCHECKED_CAST")
    fun get(index: Int): E {
        require(index < get)
        return array[index].value!!.getValue() as E
    }*/
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME