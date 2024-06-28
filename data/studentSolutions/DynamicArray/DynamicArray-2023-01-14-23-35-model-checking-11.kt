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
    override fun get(index: Int): E {
        if (index > size) {
            throw IllegalArgumentException("Index [$index] '>', than size [$size]")
        }
        return core.value.get(index)
    }
    override fun put(index: Int, element: E) {
        while (true) {
            if (index > size) {
                throw IllegalArgumentException("Index [$index] '>', than size [$size]")
            }
            val iV = InsertValue(element, false)
            val curElement = core.value.array[index].value // ToDo:: check
            if (core.value.array[index].compareAndSet(curElement, iV)) {
                return
            } else {
                if (core.value.array[index].value!!.getValue() == element)
                    return
            }
        }
    }
    override fun pushBack(element: E) {
        while (true) {
            val curSize = core.value._size.value
            val newSize = curSize + 1
            if (core.value._capacity.value > newSize) {
                val iV = InsertValue(element, false)
                if (core.value._size.compareAndSet(curSize, newSize)) {
                    if (core.value.array[newSize].compareAndSet(null, iV)) {
                        return
                    }
                }
            } else {
                val curCore = core.value
                val newCore = Core<E>(curCore._capacity.value * 2, curSize)
                for (i in 0 until curSize) {
                    if (curCore.array[i].value == null) {
                        continue
                    }
                    val tV = curCore.array[i].value
                    if (curCore.array[i].value!!.setWrite(true)) {
                        newCore.array[i].compareAndSet(null, tV) // ToDo:: check this
                    }
                }
                core.compareAndSet(curCore, newCore)
                continue
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