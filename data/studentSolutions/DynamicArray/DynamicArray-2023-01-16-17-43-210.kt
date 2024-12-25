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
    private enum class State {
        NONE, PROCESS
    }

    private val lock = atomic(false)
    private val state = atomic(State.NONE)

    override fun get(index: Int): E = core.value[index]

    @Suppress("ControlFlowWithEmptyBody")
    override fun put(index: Int, element: E) {
        while (!state.compareAndSet(State.NONE, State.NONE));

        require(index in 1 until size) { "$index is out of bounds" }

        core.value[index] = element
    }

    @Suppress("BooleanLiteralArgument", "ControlFlowWithEmptyBody")
    override fun pushBack(element: E) {
        while (!state.compareAndSet(State.NONE, State.PROCESS));
        while (!lock.compareAndSet(false, true));

        core.value.expand(element)

        lock.getAndSet(true)
        state.getAndSet(State.NONE)
    }

    override val size: Int get() = core.value.size
}

private class Core<E>(
    private val capacity: Int,
) {
    private val array = atomicArrayOfNulls<E>(capacity)
    private val _size = atomic(0)

    val size: Int = _size.value

    @Suppress("UNCHECKED_CAST")
    operator fun get(index: Int): E {
        require(index < size)
        return array[index].value as E
    }

    operator fun set(index: Int, e: E) {
        array[index].getAndSet(e)
    }

    fun expand(e: E) {
        _size.getAndIncrement()

        require(size < capacity) { "Too many elements" }

        set(size, e)
    }
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME
