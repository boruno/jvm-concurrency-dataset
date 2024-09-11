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
        if (index >= core.size.value) {
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
            val core = core.value
            val size = core.size.value
            if (size < core.capacity) {
                core.casSize(size, size + 1)
                if (core.cas(size, null, element)) {
                    return
                }
            } else {
                val newNode = Core<E>(2 * core.capacity)
                newNode.setSize(core.capacity)
                if (core.next.compareAndSet(null, newNode)) {
                    for (i in 1..core.capacity) {
                        if (core.get(i - 1) != null) {
                            newNode.cas(i - 1, null, core.get(i - 1))
                        }
                    }

                    this.core.compareAndSet(core, newNode)
                } else {
                    if (core.next.value != null) {
                        for (i in 1..core.capacity) {
                            if (core.get(i - 1) != null) {
                                core.next.value!!.array[i - 1].compareAndSet(null, core.get(i - 1))
                            }
                        }

                        this.core.compareAndSet(core, core.next.value!!)
                    }
                }
            }
        }
    }

    override val size: Int get() = core.value.size.value
}

private class Core<E>(
    var capacity: Int
) {
    val array = atomicArrayOfNulls<E>(capacity)
    val size = atomic(0)

    val next = atomic<Core<E>?>(null)

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E {
        require(index < size.value)
        return array[index].value as E
    }

    fun put(index: Int, element: E) {
        array[index].getAndSet(element)
    }

    fun cas(index: Int, expected: E?, update: E?): Boolean {
        return array[index].compareAndSet(expected, update)
    }

    fun casSize(expected: Int, update: Int): Boolean {
        return size.compareAndSet(expected, update)
    }

    fun setSize(value: Int) {
        size.getAndSet(value)
    }
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME