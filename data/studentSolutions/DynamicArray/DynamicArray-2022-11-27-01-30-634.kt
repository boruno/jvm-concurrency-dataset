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
    private val core = atomic(Core<State<E>>(INITIAL_CAPACITY))
    private val next = atomic(core.value)
    private val _size = atomic(0)

    override fun get(index: Int): E {
        while (true) {
            require(index < core.value._size.value)
            val value = core.value.array[index].value!!
            if (!value.isConst()) {
                return value.element()
            }
            next.value.array[index].compareAndSet(null, State(value.element()))
            val nextValue = next.value.array[index].value ?: continue;
            return nextValue.element()
        }
    }

    override fun put(index: Int, element: E) {
        while (true) {
            require(index < core.value._size.value)
            val value = core.value.array[index].value!!
            if (!value.isConst()) {
                if (core.value.array[index].compareAndSet(value, State(element))) {
                    return
                }
                continue
            }
            val nextArray = next.value
            nextArray.array[index].value = State(element)
            if (nextArray == next.value) return
        }
    }

    override fun pushBack(element: E) {
        while (true) {
            val index = core.value._size.value
            if (index >= core.value.capacity) {
                new(index)
            } else if (core.value.array[index].compareAndSet(null, State(element))) {
                core.value._size.compareAndSet(index, index + 1)
                return
            } else {
                core.value._size.compareAndSet(index, index + 1)
            }
        }
    }

    private fun new(index: Int) {
        val currentArray = core.value
        if (index < currentArray.capacity) return
        println("push")
        next.compareAndSet(currentArray, Core(currentArray.capacity * 2))
        next.value._size.compareAndSet(0, currentArray.size)
        val nextArray = next.value
        for (i in 0 until currentArray.size) {
            do {
                val v = currentArray.array[i].value!!
            } while (
                !v.isConst() &&
                !currentArray.array[i].compareAndSet(v, State(v.element()).asConst())
            )
            nextArray.array[i].compareAndSet(null, State(currentArray.array[i].value!!.element()))
        }
        core.compareAndSet(currentArray, nextArray)
    }

    override val size: Int get() = core.value.size
}

private class State<E>(
    e: E,
) {

    var el: E = e
    var state: Int = 0

    fun isConst(): Boolean {
        return state == 1
    }

    fun asConst(): State<E> {
        state = 1
        return this
    }

    fun isMoved(): Boolean {
        return state == 2
    }

    fun asMoved(): State<E> {
        state = 2
        return this
    }

    fun element(): E {
        return el
    }
}

private class Core<E>(
    capacity: Int,
) {
    public val array = atomicArrayOfNulls<E>(capacity)
    public val _size = atomic(0)

    val size: Int get() = _size.value
    val capacity: Int get() = array.size

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E {
        require(index < size)
        return array[index].value as E
    }
}


private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME