//package mpp.dynamicarray

import kotlinx.atomicfu.*
import java.lang.IllegalArgumentException

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
    val _size = atomic(0)
    override fun get(index: Int): E {
        if (index > size) {
            throw IllegalArgumentException()
        }
        return core.value.array.get(index).value!!.content
    }

    override fun put(index: Int, element: E) {
        if (index >= _size.value) {
            throw IllegalArgumentException()
        }

        val value = core.value.array[index].value
        if (!core.value.next.compareAndSet(null, null)) {
            if (value != null && !value.moving.value) {
                if (value.moving.compareAndSet(false, true)) {
                    val next = core.value.next.value
                    var newValue = value
                    newValue.moving.compareAndSet(true, false)
                    next!!.array[index].compareAndSet(null, newValue)
                    return
                }
            }
        }
        core.value.array[index].compareAndSet(value, Cell(element))
    }

    override fun pushBack(element: E) {
        while (true) {
            val size = _size.value
            val core = core.value
            if (size >= core.capacity) {
                moveCore()
                continue
            }
            if (core.array[size].compareAndSet(null, Cell(element))) {
                increaseSize()
                return
            }
        }
    }

    private fun increaseSize() {
        _size.incrementAndGet()
    }

    private fun moveCore() {
        val core = this.core.value
        val next = Core<E>(core.capacity * 2)
        if (!core.next.compareAndSet(null, next)) {
            return
        }
        for (i in 0 until core.capacity) {
            val el = core.array[i].value
            if (el!!.moving.compareAndSet(false, true)) {
                next.array[i].compareAndSet(null, el)
                next.array[i].value!!.moving.compareAndSet(true, false)
            }
        }
        this.core.compareAndSet(core, core.next.value!!)
    }

    override val size: Int get() = _size.value
}

private class Core<E>(
    val capacity: Int,
) {
    val array = atomicArrayOfNulls<Cell<E>>(capacity)
    val next: AtomicRef<Core<E>?> = atomic(null)
}

private class Cell<E>(val content: E) {
    val moving: AtomicBoolean = atomic(false)
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME