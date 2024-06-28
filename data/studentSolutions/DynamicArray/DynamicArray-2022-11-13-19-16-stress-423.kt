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

    override fun get(index: Int): E {
        while (true) {
            val oldCore = core.value
            val element = oldCore.get(index)
            if (element != null) {
                return element
            }
            if (oldCore == core.value) {
                oldCore.moveElements()
                core.compareAndSet(oldCore, oldCore.next)
            }
        }
    }

    override fun put(index: Int, element: E) {
        while (true) {
            val oldCore = core.value
            if (oldCore.put(index, element)) {
                return
            }
            if (oldCore == core.value) {
                oldCore.moveElements()
                core.compareAndSet(oldCore, oldCore.next)
            }
        }
    }

    override fun pushBack(element: E) {
        while (true) {
            val oldCore = core.value
            if (oldCore.pushBack(element)) {
                return
            }
            if (oldCore == core.value) {
                oldCore.moveElements()
                core.compareAndSet(oldCore, oldCore.next)
            }
        }
    }

    override val size: Int get() = core.value.size
}

private class Core<E>(
    capacity: Int,
) {
    private val array = atomicArrayOfNulls<Any>(capacity)
    private val _size = atomic(0)
    private val _next: AtomicRef<Core<E>?> = atomic(null)

    val size: Int get() = _size.value

    val next: Core<E>
        get() {
            val curNext = _next.value
            if (curNext != null) {
                return curNext
            }
            val newNext = Core<E>(array.size * 2)
            newNext._size.getAndSet(array.size)
            _next.compareAndSet(null, newNext)
            return _next.value!!
        }

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E? {
        require(index < size)
        val element = array[index].value
        if (element == S) {
            return null
        }
        if (element is Box<*>) {
            return element.element as E
        }
        return element as E
    }

    fun put(index: Int, element: E): Boolean {
        require(index < size)
        while (true) {
            val oldElement = array[index].value
            if (element == S || element is Box<*>) {
                return false
            }
            if (array[index].compareAndSet(oldElement, element)) {
                return true
            }
        }
    }

    fun pushBack(element: E): Boolean {
        while (true) {
            val index = size
            if (index == array.size) {
                return false
            }
            if (array[index].compareAndSet(null, element)) {
                _size.compareAndSet(index, index + 1)
                return true
            }
            _size.compareAndSet(index, index + 1)
        }
    }

    fun moveElements() {
        val next = this.next
        for (i in 0 until array.size) {
            while (true) {
                val element = array[i].value
                if (element is Box<*>) {
                    next.array[i].compareAndSet(null, element.element)
                    array[i].getAndSet(S)
                } else if (element != S) {
                    if (!array[i].compareAndSet(element, Box(element))) {
                        continue
                    }
                    next.array[i].compareAndSet(null, element)
                    array[i].getAndSet(S)
                }
                break
            }
        }
    }

    private data class Box<T>(val element: T)

    companion object {
        val S = Any()
    }
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME
