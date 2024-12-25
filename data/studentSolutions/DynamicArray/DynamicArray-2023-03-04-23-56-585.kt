//package mpp.dynamicarray

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
        var cur: Core<E>? = core.value
        while (cur != null) {
            cur.set(index, element)
            cur = cur.next.value
        }
    }

    override fun pushBack(element: E) {
        while (true) {
            val cur = core.value
            val size = cur.size
            val capacity = cur.capacity
            if (size < capacity) {
                if (cur.cas(size, null, element)) {
                    cur.casSize(size, size + 1)
                    return
                }
                cur.casSize(size, size + 1)
            } else {
                val next = Core<E>(capacity * 2, capacity)
                cur.next.compareAndSet(null, next)
                for (i in 0 until capacity) {
                    next.cas(i, null, cur.get(i))
                }
                core.compareAndSet(cur, cur.next.value!!)
            }
        }
    }

    override val size: Int get() = core.value.size
}

private class Core<E>(
    val capacity: Int,
    val initSize: Int = 0
) {
    private val array = atomicArrayOfNulls<E>(capacity)
    private val _size = atomic(initSize)
    val next: AtomicRef<Core<E>?> = atomic(null)

    val size: Int
        get() = _size.value

//    val capacity = capacity

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E {
        require(index < size)
        return array[index].value as E
    }

    fun set(index: Int, value: E) {
        require(index < size)
        array[index].getAndSet(value)
    }

    fun cas(index: Int, expected: E?, update: E): Boolean {
        require(index < capacity)
        return array[index].compareAndSet(expected, update)
    }

    fun casSize(expected: Int, update: Int): Boolean {
        return _size.compareAndSet(expected, update)
    }


}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME