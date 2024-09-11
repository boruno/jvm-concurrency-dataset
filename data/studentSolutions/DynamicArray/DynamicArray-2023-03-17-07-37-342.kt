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
        var core = core.value
        if (index >= core.size) {
            throw IllegalArgumentException()
        }

        core.put(index, element)
        while (true) {
            val curValue = core.get(index)
            val nextValue = core.next.value
            if (curValue != null && nextValue != null) {
                nextValue.put(index, core.get(index))
                core = nextValue
            } else {
                break
            }
        }
    }

    override fun pushBack(element: E) {
        while (true) {
            val curCore = core.value
            val size = curCore.size
            if (size < curCore.capacity) {
                if (curCore.cas(size, null, element)) {
                    curCore.casSize(size, size + 1)
                    return
                } else {
                    curCore.casSize(size, size + 1)
                }
            } else {
                val newNode = Core<E>(2 * curCore.capacity)
                newNode.setSize(curCore.capacity)
                if (curCore.next.compareAndSet(null, newNode)) {
                    for (i in 1..curCore.capacity) {
                        val value = curCore.get(i - 1)
                        if (value != null) {
                            newNode.cas(i - 1, null, value)
                        }
                    }

                    core.compareAndSet(curCore, newNode)
                } else {
                    if (curCore.next.value != null) {
                        for (i in 1..curCore.capacity) {
                            val value = curCore.get(i - 1)
                            if (value != null) {
                                curCore.next.value!!.cas(i - 1, null, value)
                            }
                        }

                        core.compareAndSet(curCore, curCore.next.value!!)
                    }
                }
            }
        }
    }

    override val size: Int get() = core.value.size
}

private class Core<E>(
    var capacity: Int
) {
    private val array = atomicArrayOfNulls<E>(capacity)
    private val _size = atomic(0)

    val size: Int = _size.value

    val next = atomic<Core<E>?>(null)

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E {
        require(index < size)
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

    fun setSize(value: Int) {
        _size.getAndSet(value)
    }
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME