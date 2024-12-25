//package mpp.dynamicarray

import kotlinx.atomicfu.*
import java.lang.IndexOutOfBoundsException

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
    private val curSize = atomic(0)

    override fun get(index: Int): E {
        if (index < 0 || index >= size) {
            throw IllegalArgumentException("Index out of bounds")
        }
        while (true) {
            if (core.value.capacity > index) {
                val res = core.value.array[index].value
                if (res != null) {
                    return res
                }
            }
        }
    }

    override fun put(index: Int, element: E) {
        if (index < 0 || index >= size) {
            throw IllegalArgumentException("Index out of bounds")
        }
        while (true) {
            if (core.value.capacity > index) {
                core.value.array[index].getAndSet(element)
                return
            }
        }
    }

    override fun pushBack(element: E) {
        val newSize = curSize.getAndIncrement()
        while (true) {
            if (newSize < core.value.capacity) {
                if (core.value.array[newSize].compareAndSet(null, element)) {
                    return
                }
            } else {
                resize()
            }
        }
    }

    private fun resize() {
        val oldCore = core.value
        val newCore = Core<E>(oldCore.capacity * 2)
        if (oldCore.nextCore.compareAndSet(null, newCore)) {
            for (i in 0 until oldCore.capacity) {
                newCore.array[i].value = oldCore.array[i].value
            }
            core.compareAndSet(oldCore, newCore)
        }
    }


    override val size: Int get() = curSize.value
}


public class Core<E>(
    val capacity: Int,
) {
    val nextCore : AtomicRef<Core<E>?> = atomic(null)
    val array = atomicArrayOfNulls<E>(capacity)
    val size = atomic(0)

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E {
        require(index < size.value)
        return array[index].value as E
    }
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME