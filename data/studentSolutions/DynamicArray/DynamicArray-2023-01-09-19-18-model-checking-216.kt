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

//data class FixedValue<E>(val value: E)

class DynamicArrayImpl<E> : DynamicArray<E> {
    private val core = atomic(Core<E>(INITIAL_CAPACITY))

    override fun get(index: Int): E = core.value.get(index)

    override fun put(index: Int, element: E) {
        require(index < size)

        while (true) {
            val currentValue = core.value.get(index)

            if (currentValue != null) {
                val value = core.value.array[index].getAndSet(null)
                if (value != null) {
                    core.value.array[index].compareAndSet(null, value)
                    return
                }
            }
        }
    }

    override fun pushBack(element: E) {
        while (true) {
            val oldArray = core.value

            val newArray = Core<E>(oldArray.size * 2)

            if (core.value.next.compareAndSet(null, newArray)) {
                for (i in 0 until oldArray.size) {
                    val oldValue = oldArray.array[i].getAndSet(null)

                    if (oldValue != null) {
                        newArray.array[i].value = oldValue
                    }
                }
                newArray.array[oldArray.size].value = element
                // move elements
                core.compareAndSet(oldArray, newArray)
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
    val next: AtomicRef<Core<E>?> = atomic(null)

    val size: Int = _size.value

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E {
        require(index < size)
        return array[index].value as E
    }
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME