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
        require(index < size)

        var core: Core<E>? = core.value
        while (true) {
            core!!.array[index].getAndSet(element)
            core = core.next.value
            if (core == null) {
                return
            }
        }
    }

    override fun pushBack(element: E) {
        while (true) {
            val curCore = core.value
            val size = curCore.size

            if (size < curCore.capacity) {
                if (!curCore.array[size].compareAndSet(null, element)) {
                    curCore._size.compareAndSet(size, size + 1)
                    continue
                }

                curCore._size.compareAndSet(size, size + 1)
                return
            }

            val newCapacity = curCore.capacity * 2
            val newCore = Core<E>(newCapacity)

            newCore._size.compareAndSet(0, size)

            curCore.next.compareAndSet(null, newCore)
            for (i in 0 until size) {
                curCore.next.value!!.array[i].compareAndSet(null, curCore.array[i].value)
            }

            this.core.compareAndSet(curCore, curCore.next.value!!)
        }
    }

    override val size: Int get() = core.value.size
}

private class Core<E>(
    val capacity: Int,
) {
    val _size = atomic(0)
    val array = atomicArrayOfNulls<E>(capacity)
    val size: Int = _size.value
    val next = atomic<Core<E>?>(null)

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E {
        require(index < size)
        return array[index].value as E
    }
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME