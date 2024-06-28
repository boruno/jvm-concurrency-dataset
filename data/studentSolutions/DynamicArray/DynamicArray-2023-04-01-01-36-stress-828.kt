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
    override val size: Int get() = core.value.size.value

    override fun get(index: Int): E {
        if (index >= size) throw IllegalArgumentException("Index exceeds array size")
        return core.value.array[index].value ?: throw IllegalArgumentException("Index exceeds array size")
    }

    override fun put(index: Int, element: E) {
        if (index >= size) throw IllegalArgumentException("Index exceeds array size")
        core.value.array[index].getAndSet(element)
        while (core.value.next.value != null) {
            core.value = core.value.next.value!!
            core.value.array[index].getAndSet(element)
        }
    }

    override fun pushBack(element: E) {
        while (true) {
            val curHead = core.value
            val curSize = curHead.size.value

            if (curSize < curHead.capacity) {
                val flag = curHead.array[curSize].compareAndSet(null, element)

                curHead.size.compareAndSet(curSize, curSize + 1)

                if (flag) return else continue
            }

            var next: Core<E>? = Core(2 * curHead.capacity)

            if (!curHead.next.compareAndSet(null, next)) next = curHead.next.value

            if (next != null) {
                for (i in 0 until curHead.capacity) next.array[i].compareAndSet(null, curHead.array[i].value)
                core.compareAndSet(curHead, next)
            }
        }
    }
}

private class Core<E>(val capacity: Int) {
    val size = atomic(capacity / 2)
    val array = atomicArrayOfNulls<E>(capacity)
    val next: AtomicRef<Core<E>?> = atomic(null)
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME