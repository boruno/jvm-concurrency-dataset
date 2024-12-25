//package mpp.dynamicarray

import kotlinx.atomicfu.*
import kotlinx.atomicfu.AtomicRef
import java.util.concurrent.ThreadLocalRandom

class InsertValue <E> {
    private val _value: AtomicRef<E?> = atomic(null)
    private val _write = atomic(false)
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
    private val core = atomic(Core<E>(INITIAL_CAPACITY, 0))
    override fun get(index: Int): E {
        if (index > size) {
            throw IllegalArgumentException("Index [$index] '>', than size [$size]")
        }
        return core.value.get(index)
    }
    override fun put(index: Int, element: E) {
        while (true) {
            if (index >= size) {
                throw IllegalArgumentException("Index [$index] '>', than size [$size]")
            }
            val iV = InsertValue(element, false)
            val curElement = core.value.array[index].value // ToDo:: check
            if (curElement!!.isWriting()) continue
            //if (core.value.array[index].compareAndSet(null, iV)) core.value._size.incrementAndGet()
            if (core.value.array[index].compareAndSet(curElement, iV)) {
                return
            } else {
                if (core.value.array[index].value == element)
                    return
            }
        }
    }
    override fun pushBack(element: E) {
        var globalSize = 0
        while (true) {
            val curCore = core.value
            var curSize = curCore._size.value
            if (globalSize > curSize)
                curSize = globalSize
            if (curSize > curCore._capacity.value) { // Need update
                if (curCore._needResize.compareAndSet(false, true)) {
                    val newCore = Core<E>(curCore._capacity.value * 2, curSize)
                    for (i in 0 until curSize) {
                        val curElement = curCore.array[i].value
                        if (curElement!!.setWrite(true)) {
                            newCore.array[i].getAndSet(curElement)
                        }
                    }
                }
            }
            else if (curCore._capacity.value > curSize) {
                val iV = InsertValue<E>(element, false)
                if (core.value.array[curSize].compareAndSet(null, iV) && core.value._size.compareAndSet(curSize, curSize + 1)) {
                    return
                }
                else {
                    globalSize += 1
                }
            }
        }

    }
    override val size: Int get() = core.value._size.value
}

private class Core<E>(capacity: Int, size: Int) {
    val array = atomicArrayOfNulls<InsertValue<E>>(capacity)
    val _size = atomic(size)
    val _capacity = atomic(capacity)
    val _needResize = atomic(false)


    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E {
        require(index < _size.value)
        return array[index].value as E
    }
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME