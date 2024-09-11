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

    override fun get(index: Int): E = core.value[index]

    override fun put(index: Int, element: E) {
        require(index < core.value.size)

        var cur_arr = core.value
        var cur_next = cur_arr.next.value
        cur_arr.array[index].value = element
        while (cur_next != null) {
            cur_arr = cur_next
            cur_next = cur_arr.next.value
            cur_arr.array[index].value = element
        }
    }

    override fun pushBack(element: E) {
        while (true) {
            val cur_arr = core.value
            val i = cur_arr.gai()
            if (i >= cur_arr.array.size) {
                val next_core = Core<E>(cur_arr.array.size shl 1)
                if (!cur_arr.next.compareAndSet(null, next_core)) {
                    continue
                }
                for (j in 0 until cur_arr.array.size) {
                    next_core.array[j].value = cur_arr.array[j].value
                    next_core.gai()
                }
                next_core.array[cur_arr.array.size].value = element
                next_core.gai()
            } else {
                if (cur_arr.array[i].compareAndSet(null, element)) {
                    return
                }
            }
        }
    }

    override val size: Int get() = core.value.size
}

private class Core<E>(
    capacity: Int,
) {
    val array = atomicArrayOfNulls<E>(capacity)
    private val _size = atomic(0)
    val next = atomic<Core<E>?>(null)

    val size: Int get() = _size.value

    @Suppress("UNCHECKED_CAST")
    operator fun get(index: Int): E {
        require(index < size)
        return array[index].value as E
    }

    fun gai(): Int {
        return _size.getAndIncrement()
    }
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME