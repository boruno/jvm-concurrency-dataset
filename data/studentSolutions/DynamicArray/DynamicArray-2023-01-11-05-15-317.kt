package mpp.dynamicarray

import kotlinx.atomicfu.*

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
        val currentCore = core.value
        currentCore.put(index, element)
    }

    override fun pushBack(element: E) {
        val currentCore = core.value
        val newCore = currentCore.pushBack(element)
        core.compareAndSet(currentCore, newCore)
    }

    override val size: Int get() = core.value.size
}

private class Core<E>(
    private val capacity: Int,
) {
    private val array = atomicArrayOfNulls<Any?>(capacity)
    private val _size = atomic(0)
    private val next = atomic<Core<E>?>(null)

    val size: Int
        get() {
            return _size.value
        }

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E {
        require(index < size)
        val v = array[index].value
        if (v !is Moved) {
            return v as E
        }
        return findNext().get(index)
    }

    fun put(index: Int, value: E) {
        require(index < size)
        while (true) {
            val prevValue = array[index].value
            if (prevValue == Moved) {
                findNext().put(index, value)
            }
            if (array[index].compareAndSet(prevValue, value)) {
                return
            }
        }
    }

    fun pushBack(element: E): Core<E> {
        val pos = _size.getAndIncrement()
        if (pos < capacity) {
            while (true) {
                val v = array[pos].value
                if (v is Moved) {
                    findNext().put(pos, element)
                }
                if (array[pos].compareAndSet(v, element)) {
                    break
                }
            }
            return this
        }
        val newCore = findNext().takeIf { it != this } ?: (Core<E>(capacity * 2).also { it._size.value = pos + 1 })
        next.compareAndSet(null, newCore)
        val nextCore = next.value!!
        for (i in 0 until capacity) {
            while (true) {
                val value = array[i].value
                if (value is Moved) break
                if (!nextCore.array[i].compareAndSet(null, value)) {
                    break
                }
                if (array[i].compareAndSet(value, Moved)) {
                    break
                }
            }
        }
        return nextCore.pushBack(element)
    }

    fun findNext(): Core<E> {
        val currentNextCore = this
        var nextCore = this
        while (true) {
            val newNextCore = nextCore.next.value ?: break
            nextCore = newNextCore
        }
        next.compareAndSet(currentNextCore, nextCore)
        return nextCore
    }
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME

private object Moved
