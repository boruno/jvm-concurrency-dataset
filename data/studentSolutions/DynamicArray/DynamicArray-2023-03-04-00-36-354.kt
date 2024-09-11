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
        var cur: Core<E>? = core.value
        while (cur != null) {
            if (cur.get(index) == null) {
                throw IllegalArgumentException()
            }
            cur.set(index, element)
            cur = cur.next.value
        }
    }

    override fun pushBack(element: E) {
        while (true) {
            val cur = core.value
            val size = cur.updateSize()
            val capacity = cur.capacity
            if (size < capacity) {
                if (cur.cas(size, null, element)) {
                    return
                }
            } else {
                val next = Core<E>(capacity * 2)
                next.size = capacity
                cur.next.compareAndSet(null, next)
                for (i in 0 until capacity) {
                    next.cas(i, null, cur.get(i))
                }
                core.compareAndSet(cur, next)
            }
        }
    }

    override val size: Int get() = core.value.size
}

private class Core<E>(
    val capacity: Int,
) {
    private val array = atomicArrayOfNulls<E>(capacity)
    private val _size = atomic(0)

    var size: Int
        get() = _size.value
        set(value) {
            _size.compareAndSet(0, value)
        }
//    val capacity = capacity
    val next: AtomicRef<Core<E>?> = atomic(null)

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

    fun updateSize(): Int {
        return _size.getAndIncrement()
    }

}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME