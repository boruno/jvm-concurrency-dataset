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
    override val size: Int get() = core.value.size.value

    override fun get(index: Int): E {
        if (index >= size || index < 0) throw IllegalArgumentException("Invalid index")
        return core.value.array[index].value!!
    }

    override fun put(index: Int, element: E) {
        if (index >= size || index < 0) throw IllegalArgumentException("Invalid index")
        var head = core.value
        while (true) {
            head.array[index].getAndSet(element)
            if (head.next.value == null) break
            head = head.next.value!!
        }
    }

    override fun pushBack(element: E) {
        while (true) {
            val head = core.value
            val size = head.size.value

            if (size < head.capacity) {
                val flag = head.array[size].compareAndSet(null, element)
                head.size.compareAndSet(size, size + 1)
                if (flag) break else continue
            }

            var next: Core<E>? = Core(head.capacity * 2)
            if (!head.next.compareAndSet(null, next)) next = head.next.value

            if (next == null) continue

            for (i in 0 until head.capacity) next.array[i].compareAndSet(null, head.array[i].value)
            core.compareAndSet(head, next)
        }
    }
}

private class Core<E>(val capacity: Int) {
    val size = atomic(0)
    val array = atomicArrayOfNulls<E>(capacity)
    val next: AtomicRef<Core<E>?> = atomic(null)
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME