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
    private val core = atomic(Core<State<E>>(INITIAL_CAPACITY))
    private val next = atomic<Core<State<E>>?>(null)
    private val pre_size = atomic(0)
    private val _size = atomic(0)

    override fun get(index: Int): E {
        while (true) {
            require(index < _size.value)
            val value = core.value.array[index].value
            if (value != null) {
                return value.element()
            }
            val nextArray = next.value ?: continue
            val e = nextArray.array[index].value ?: continue
            return e.element()
        }
    }

    override fun put(index: Int, element: E) {
        while (true) {
            require(index < _size.value)
            val value = core.value.array[index].value
            if (value != null) {
                if (!value.isConst()) {
                    if (core.value.array[index].compareAndSet(value, State(element))) {
                        return
                    }
                    continue
                }
                val nextArray = next.value ?: continue
                nextArray.array[index].compareAndSet(null, State(value.element()))
            }
            val nextArray = next.value ?: continue
            val nextValue = nextArray.array[index].value ?: continue
            if (nextValue.isConst()) continue
            if (nextArray.array[index].compareAndSet(nextValue, State(element))) {
                return
            }
        }
    }

    override fun pushBack(element: E) {
        val index = pre_size.getAndIncrement()
        while (true) {
            val currentArray = core.value
            if (index >= currentArray.capacity) {
                new()
            } else {
                currentArray.array[index].compareAndSet(null, State(element))
                _size.incrementAndGet()
                return
            }
        }
    }

    private fun new() {
        val currentArray = core.value
        val capacity = currentArray.array.size
        next.compareAndSet(null, Core(capacity * 2))
        val nextArray = next.value ?: return
        for (i in 0 until capacity) {
            val v = currentArray.array[i].value ?: continue
            currentArray.array[i].compareAndSet(v, State(v.element()).asConst())
            nextArray.array[i].compareAndSet(null, State(v.element()))
        }
        core.compareAndSet(currentArray, nextArray)
        next.getAndSet(null)
    }


    override val size: Int get() = _size.value
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

    fun element(): E {
        return el
    }
}

private class Core<E>(
    capacity: Int,
) {
    val array = atomicArrayOfNulls<E>(capacity)
    val capacity: Int get() = array.size
}


private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME