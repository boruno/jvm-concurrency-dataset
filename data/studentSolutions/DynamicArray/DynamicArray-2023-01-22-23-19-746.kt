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

    private fun move(current: Core<E>, next: Core<E>) {
        for (i in 0 until current.capacity) {
            next.array[i].compareAndSet(null, current.array[i].value)
        }
    }

    override fun get(index: Int): E {
        if (index >= size)
            throw IllegalArgumentException()
        return core.value.array[index].value!!
    }

    override fun put(index: Int, element: E) {
        if (index >= size)
            throw IllegalArgumentException()
        var current: Core<E> = core.value
        while (true) {
            current.array[index].getAndSet(element)
            val next: Core<E>? = current.next.value
            if (next != null) {
                move(current, next)
                current = next
            } else {
                return
            }
        }
    }

    override fun pushBack(element: E) {
        while (true) {
            val current = this.core.value
            val size = current.size
            if (size < current.capacity) {
                val completed: Boolean = current.array[size].compareAndSet(null, element)
                current._size.compareAndSet(size, size + 1)
                if (completed) {
                    return
                }
            } else {
                current.next.compareAndSet(null, Core(current.capacity * 2, size))
                val next: Core<E> = current.next.value!!
                move(current, next)
                this.core.compareAndSet(current, next)
            }
        }
    }

    override val size: Int get() = core.value.size
}

private class Core<E>(
    val capacity: Int,
    initialSize: Int = 0
) {
    val array = atomicArrayOfNulls<E>(capacity)
    val _size = atomic(initialSize)
    val next: AtomicRef<Core<E>?> = atomic(null)

    val size: Int = _size.value

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E {
        require(index < size)
        return array[index].value as E
    }
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME