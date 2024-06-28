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

    override fun get(index: Int): E {
        require(index in 0 until size) { "index `$index` is out of bounds" }

        return core.value[index]
    }

    override fun put(index: Int, element: E) {
        require(index in 0 until size) { "$index is out of bounds" }

        core.value[index] = element
    }

    override fun pushBack(element: E) {
        val now = core.value

        val idx = now.acquireIndex()

        val update = now.expand(idx)

        @Suppress("ControlFlowWithEmptyBody")
        while (core.value.size() <= idx && !core.compareAndSet(now, update));

        core.value[idx] = element
    }

    override val size: Int
        get() = core.value.size()
}

private class Core<E>(
    private val capacity: Int,
    private val size: AtomicInt = atomic(0)
) {
    private val array = atomicArrayOfNulls<E>(capacity)

    fun acquireIndex() = size.getAndIncrement()

    fun size() = size.value

    @Suppress("UNCHECKED_CAST")
    operator fun get(index: Int): E {
        return array[index].value as E
    }

    operator fun set(index: Int, e: E) {
        array[index].getAndSet(e)
    }

    fun expand(idx: Int): Core<E> {
        if (idx >= capacity) {
            val tmp = Core<E>(size() * 2)

            repeat(capacity) { k ->
                array[k].value?.let { tmp[k] = it }
            }

            return tmp.expand(idx)
        }

        return this
    }
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME
