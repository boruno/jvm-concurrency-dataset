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

    override fun get(index: Int): E = core.value[index]

    override fun put(index: Int, element: E) {
        require(index in 0 until size) { "$index is out of bounds" }

        core.value[index] = element
    }

    override fun pushBack(element: E) {
        val idx = core.value.acquireIndex()

        val toUpdate = core.value
        val new = toUpdate.expand(idx, element)

        if (!core.compareAndSet(toUpdate, new)) {
            core.value[idx] = element
        }
    }

    override val size: Int
        get() = core.value.size
}

private class Core<E>(
    private val capacity: Int,
) {
    private val array = atomicArrayOfNulls<E>(capacity)
    private val _size = atomic(0)

    fun acquireIndex() = _size.getAndIncrement()
    val size: Int get() = _size.value

    @Suppress("UNCHECKED_CAST")
    operator fun get(index: Int): E {
        require(index in 0 until size) { "index `$index` is out of bounds" }
        return array[index].value as E
    }

    operator fun set(index: Int, e: E) {
        array[index].getAndSet(e)
    }

    fun expand(idx: Int, e: E): Core<E> {
        if (idx >= capacity) {
            val tmp = Core<E>(capacity * 2)

            repeat(capacity) { k ->
                array[k].value?.let { tmp[k] = it }
            }

            return tmp.expand(idx, e)
        }

        set(idx, e)
        _size.getAndSet(idx + 1)

        return this
    }
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME
