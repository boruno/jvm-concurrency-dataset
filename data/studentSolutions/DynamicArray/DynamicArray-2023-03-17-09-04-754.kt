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
        var curCore = core.value
        if (index >= curCore.getSize()) {
            throw IllegalArgumentException()
        }

        curCore.put(index, element)
        while (true) {
            val curValue = curCore.get(index)
            val nextCore = curCore.next.value
            if (curValue != null && nextCore != null) {
                nextCore.put(index, curValue)
                curCore = nextCore
            } else {
                break
            }
        }
    }

    override fun pushBack(element: E) {
        while (true) {
            val curCore = core.value
            val size = curCore.getSize()
            if (size < curCore.capacity) {
                if (curCore.cas(size, null, element)) {
                    curCore.casSize(size, size + 1)
                    return
                } else {
                    curCore.casSize(size, size + 1)
                }
            } else {
                val otherCore: Core<E>
                val nextCore = curCore.next.value
                if (nextCore == null) {
                    val newCore = Core<E>(2 * curCore.capacity)
                    newCore.casSize(0, curCore.capacity)
                    curCore.next.compareAndSet(null, newCore)
                    otherCore = newCore
                } else {
                    otherCore = nextCore
                }

                curCore.fillOther(otherCore)
                core.compareAndSet(curCore, otherCore)
            }
        }
    }

    override val size: Int get() = core.value.getSize()
}

private class Core<E>(
    val capacity: Int
) {
    private val array = atomicArrayOfNulls<E>(capacity)
    private val _size = atomic(0)

    fun getSize(): Int {
        return _size.value
    }

    val next = atomic<Core<E>?>(null)

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E {
        require(index < getSize())
        return array[index].value as E
    }

    fun put(index: Int, element: E) {
        array[index].getAndSet(element)
    }

    fun cas(index: Int, expected: E?, update: E?): Boolean {
        return array[index].compareAndSet(expected, update)
    }

    fun casSize(expected: Int, update: Int): Boolean {
        return _size.compareAndSet(expected, update)
    }

    fun fillOther(otherCore: Core<E>) {
        for (i in 0 until capacity) {
            val value = get(i)
            if (value != null) {
                otherCore.cas(i, null, value)
            }
        }
    }
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME