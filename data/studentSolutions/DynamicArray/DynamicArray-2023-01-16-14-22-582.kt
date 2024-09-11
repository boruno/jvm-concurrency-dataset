package mpp.dynamicarray

import kotlinx.atomicfu.*

data class FixedValue<E>(val value: E)

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
    private val _size = atomic(0)
    override val size: Int = _size.value

    override fun get(index: Int): E {
        if (index < 0 || index >= size) {
            throw IndexOutOfBoundsException("Index $index out of bounds [0, $size)")
        }
        return core.value.get(index)
    }

    override fun put(index: Int, element: E) {
        if (index < 0 || index >= size) {
            throw IndexOutOfBoundsException("Index $index out of bounds [0, $size)")
        }
        while (true) {
            val curCore = core.value
            val curVal = curCore.array[index].value
            if (curVal is FixedValue<*>) {
                continue
            }
            curCore.array[index].compareAndSet(curVal, element)
            return
        }
    }

    override fun pushBack(element: E) {
        val newSize = _size.getAndIncrement()
        while (true) {
            val curCore = core.value
            if (newSize < curCore.capacity.value && curCore.array[newSize].compareAndSet(null, element)) {
                return
            } else {
                resize(curCore)
            }
        }
    }

    private fun resize(oldCore: Core<E>) {
        val newCore = Core<E>(2 * oldCore.capacity.value)

        if (oldCore.next.compareAndSet(null, newCore)) {
            for (i in 0 until oldCore.capacity.value) {
                while (true) {
                    val element = oldCore.array[i].value
                    when (element) {
                        is FixedValue<*> -> {
                            newCore.array[i].value = element.value as E
                            break
                        }
                        else -> {
                            val e = element as E
                            if (oldCore.array[i].compareAndSet(e, FixedValue(e))) {
                                newCore.array[i].value = e
                                break
                            }
                        }
                    }
                }
            }
        }
        core.compareAndSet(oldCore, newCore)
    }

}

private class Core<E>(
    cap: Int
) {
    val array = atomicArrayOfNulls<Any>(cap)
    val capacity = atomic(cap)
    val next = atomic<Core<E>?>(null)


    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E = when (val a = array[index].value) {
            is FixedValue<*> -> a.value as E
            else -> a as E
        }

}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME