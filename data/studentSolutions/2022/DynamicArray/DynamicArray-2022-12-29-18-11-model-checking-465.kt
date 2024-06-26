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
        while (true) {
            val cur = core.value
            val next = cur.next.value
            val newArr = Core<E>(size + 1)
            for (i in 0..size) {
                if (i != index) {
                    newArr.array[i].value = cur.array[i].value
                } else {
                    newArr.array[i].value = Fixed(element)
                }
            }
            newArr.array[size + 1].value = Fixed(element)
            for (i in 0..size) {
                cur.array[i].value = Moved(cur.array[i].value)
            }
            if (cur == core.value) {
                if (next == null) {
                    if (cur.next.compareAndSet(next, newArr)) {
                        if (cur.next.value!!._size.compareAndSet(0, size + 1)) {
                            return
                        }
                    }
                }
            }
        }
    }

    override fun pushBack(element: E) {
        while (true) {
            val cur = core.value
            val next = cur.next.value
            val newArr = Core<E>(size + 1)
            for (i in 0..size) {
                newArr.array[i].value = cur.array[i].value
            }
            newArr.array[size + 1].value = Fixed(element)
            for (i in 0..size) {
                cur.array[i].value = Moved(cur.array[i].value)
            }
            if (cur == core.value) {
                if (next == null) {
                    if (cur.next.compareAndSet(next, newArr)) {
                        if (cur.next.value!!._size.compareAndSet(0, size + 1)) {
                            return
                        }
                    }
                }
            }
        }

    }

    override val size: Int get() = core.value.size
}

private class Core<E>(
    capacity: Int,
) {
    val array = atomicArrayOfNulls<Any>(capacity)
    val _size = atomic(0)
    val next = atomic<Core<E>?>(null)
    val size: Int = _size.value

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E {
        require(index < size)
        return array[index].value as E
    }
}

interface Cell<E> {
}

private class Empty<E>(val value: E? = null) : Cell<E>

private class Moved<E>(
    val value: E?,
) : Cell<E>

private class Fixed<E>(
    val value: E?,
) : Cell<E>

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME