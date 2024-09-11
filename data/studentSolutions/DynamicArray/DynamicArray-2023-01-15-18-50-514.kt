package mpp.dynamicarray

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
    private val nextCore: AtomicRef<Core<E>>? = null
    override fun get(index: Int): E {
        if (index >= size) {
            throw IllegalArgumentException("Index [$index] '>', than size [$size]")
        }
        if (core.value.array[index].value == null) throw IllegalArgumentException("Index [$index] '>', than size [$size]")
        return core.value.array[index].value!!.getValue()!!
    }
    override fun put(index: Int, element: E) { // ToDo:: Need optimized
        if (index >= size) {
            throw IllegalArgumentException("Index [$index] '>', than size [$size]")
        }
        while (true) {
            val curCore = core.value
            val iV = InsertValue(element, false)
            val curElement = curCore.array[index].value // ToDo:: check
            if (!curElement!!.isWriting() && curCore.array[index].compareAndSet(curElement, iV)) {
                return
            } else {
                if (curCore.array[index].value == element)
                    return
            }
        }
    }
    override fun pushBack(element: E) { // ToDo:: Need optimized
        while (true) {
            val curCore = core.value
            val curSize = curCore._size.value
            if (curSize >= curCore._capacity.value) { // Need update
                val newCore = Core<E>(curCore._capacity.value * 2, curSize)
                for (i in 0 until curSize) {
                    if (curCore.array[i].value == null || curCore.array[i].value!!.isWriting()) { continue }
                    val curElement = curCore.array[i].value
                    if (curCore.array[i].compareAndSet(curElement, InsertValue(curElement!!.getValue(), true))) {
                        newCore.array[i].getAndSet(curElement) // ToDo:: check this | it doesn't seem to work
                    }
                }
                core.compareAndSet(curCore, newCore)
            } else {
                val iV = InsertValue<E>(element, false)
                if (curCore._size.compareAndSet(curSize, curSize + 1) && curCore.array[curSize].compareAndSet(null, iV)) {
                    return
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


    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E {
        require(index < _size.value)
        return array[index].value as E
    }
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME