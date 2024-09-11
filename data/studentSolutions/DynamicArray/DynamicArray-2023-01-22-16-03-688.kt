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
    private val core = atomic(Core<E>(0, INITIAL_CAPACITY))

    override fun get(index: Int): E = core.value.get(index).value

    override fun put(index: Int, element: E) {
        while (true) {
            val current = core.value
            if (!current.put(index, element)) break

            val nextCore = current.next.value
            if (nextCore != null) {
                repeat(current.size) { moveElement(current, nextCore, it) }
            }
        }
    }

    private fun moveElement(current: Core<E>, nextCore: Core<E>, index: Int) {
        while (true) {
            val currentElement= current.get(index)

            if (currentElement.isMoved) {
                nextCore.arrayElementCas(index, null, Element(currentElement.value, false))
                return
            }
            val ifPut = current.arrayElementCas(index, currentElement, Element(currentElement.value, true))
            if (ifPut) {
                nextCore.arrayElementCas(index, null, Element(currentElement.value, false))
                return
            }
        }
    }

    override fun pushBack(element: E) {
        while (true) {
            val current = core.value
            var nextCore = current.next.value

            if (current.pushBack(element)) break
            if (nextCore != null) {
                repeat(current.size) { moveElement(current, nextCore!!, it) }
            } else {
                nextCore = Core(current.size, current.capacity * 2)
                if (current.next.compareAndSet(null, nextCore))
                    repeat(current.size) { moveElement(current, nextCore, it) }
            }
        }
    }

    override val size: Int get() = core.value.size
}

private class Element<E>(val value: E, var isMoved: Boolean)

private class Core<E>(
    initialSize: Int,
    val capacity: Int
) {
    val next: AtomicRef<Core<E>?> = atomic(null)
    private val array = atomicArrayOfNulls<Element<E>>(capacity)
    private val _size = atomic(initialSize)

    val size: Int = _size.value

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): Element<E> {
        require(index < size)
        return array[index].value as Element<E>
    }

    fun put(index: Int, element: E): Boolean {
        require(index < _size.value)
        while (true) {
            val current = array[index].value!!
            if (current.isMoved)
                return true

            val newElement = Element(element, false)
            if (array[index].compareAndSet(current, newElement)) break
        }
        return false
    }

    fun arrayElementCas(index: Int, expect: Element<E>?, new: Element<E>): Boolean {
        return array[index].compareAndSet(expect, new)
    }

    fun pushBack(element: E): Boolean {
        while (true) {
            val index = size
            if (index >= capacity) return false
            if (arrayElementCas(index, null, Element(element, false))) {
                _size.getAndIncrement()
                return true
            }
            _size.getAndIncrement()
        }
    }
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME