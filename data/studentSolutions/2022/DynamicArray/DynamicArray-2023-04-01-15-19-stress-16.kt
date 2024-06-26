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
    private val core: AtomicRef<Core<E>> = atomic(Core(INITIAL_CAPACITY))
    override val size: Int get() = core.value.size.value

    override fun get(index: Int): E {
        if (index >= size || index < 0) throw IllegalArgumentException("Invalid index")
        return core.value.array[index].value!!
    }

    override fun put(index: Int, element: E) {
        if (index >= size || index < 0) throw IllegalArgumentException("Invalid index")

        core.value.array[index].getAndSet(element)
        while (true) {
            val next = core.value.next.value ?: break
            core.value.array[index].value?.let { next.array[index].getAndSet(it) }
            core.value = next
        }
    }

    override fun pushBack(element: E) {
        while (true) {
            val head = core.value
            val size = head.size.value

            if (size < head.capacity) {
                val finished = head.array[size].compareAndSet(null, element)
                head.size.compareAndSet(size, size+1)
                if (finished) break else continue
            }

            var next: Core<E>? = Core(head.capacity * 2)

            if (!head.next.compareAndSet(null, next)) next = head.next.value
            if (next == null) continue
            next.size.compareAndSet(0, head.capacity)

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