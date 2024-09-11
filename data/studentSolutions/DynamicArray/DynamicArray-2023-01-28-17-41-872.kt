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
    private val core : AtomicRef<Core<E>> = atomic(Core<E>(null, INITIAL_CAPACITY, 0 ))

    override fun get(index: Int): E {
		return core.value.array[index].value!!
    }

    override fun put(index: Int, element: E) {
        var curCore = core.value
        if (index >= curCore.sizeAt.value || index < 0) {
            throw IllegalArgumentException()
        }
        curCore.array[index].compareAndSet(curCore.array[index].value, element)
        while (true) {
            val nextCore = curCore.next.value
            if (nextCore == null) {
                return
            } else {
                val value = curCore.array[index].value
                nextCore.array[index].getAndSet(value)
                curCore = nextCore
            }

        }
    }

    override fun pushBack(element: E) {
        while (true) {
            val curCore = core.value
            val curSize = curCore.sizeAt.value
            if (curSize < curCore.capacity) {
                if (curCore.array[curSize].compareAndSet(null, element)) {
                    curCore.sizeAt.compareAndSet(curSize, curSize + 1)
                    return
                } else {
                    curCore.sizeAt.compareAndSet(curSize, curSize + 1)
                }
            } else {
                val newCore = Core<E>(null, curCore.capacity * 2, curCore.capacity)
                if (curCore.next.compareAndSet(null, newCore)) {
                    for (i in 1..curCore.capacity) {
                        val value = curCore.array[i - 1].value
                        if (value != null) {
                            newCore.array[i - 1].compareAndSet(null, value)
                        }
                    }
                    core.compareAndSet(curCore, newCore)
                } else {
                    val nextCore = curCore.next.value
                    if (nextCore != null) {
                        for (i in 1..curCore.capacity) {
                            val value = curCore.array[i - 1].value
                            if (value != null) {
                                nextCore.array[i - 1].compareAndSet(null, value)
                            }
                        }
                        core.compareAndSet(curCore, nextCore)
                    }
                }
            }
        }
    }

    override val size: Int get() = core.value.sizeAt.value
}

private class Core<E>(
    next : Core<E>?,
    var capacity: Int,
    size : Int
) {
    val array = atomicArrayOfNulls<E>(capacity)
    val next = atomic(next)
    val sizeAt : AtomicInt = atomic(size)

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E {
        require(index < sizeAt.value)
        return array[index].value as E
    }
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME