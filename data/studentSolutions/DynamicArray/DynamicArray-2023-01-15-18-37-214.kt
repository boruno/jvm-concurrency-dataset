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
        while (!core.value.put(index, element)) {
        }
    }

    override fun pushBack(element: E) {
        while (true) {
            if (core.value.size >= core.value.capacity) {
                resize()
                continue
            }
            if (core.value.push_back(element)) {
                return
            }
        }
    }

    private fun resize() {
        core.compareAndSet(core.value, core.value.resize())
    }

    override val size: Int get() = core.value.size
}

private open class ArrayElement<E>(val element: E)

private class MovedElement<E>(element: E): ArrayElement<E>(element)

private class Core<E>(
    val capacity: Int,
) {
    private val array = atomicArrayOfNulls<ArrayElement<E>>(capacity)
    private val _size = atomic(0)
    private val newCore: AtomicRef<Core<E>?> = atomic(null)

    val size: Int = _size.value

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E {
        require(index < size)
        val value = array[index].value
        if (value is MovedElement<E>) {
            return newCore.value!!.get(index)
        }
        return value!!.element
    }

    fun put(index: Int, element: E): Boolean {
        require(index < size)
        val prev = array[index].value
        if (prev is MovedElement<E>) {
            return newCore.value!!.put(index, element)
        }
        return array[index].compareAndSet(prev, ArrayElement(element))
    }

    fun push_back(element: E): Boolean {
        val size = size
        if (array[size].compareAndSet(null, ArrayElement(element))) {
            _size.incrementAndGet()
            return true
        }
        return false
    }

    fun resize() : Core<E> {
        newCore.compareAndSet(null, Core(capacity * CAPACITY_INCREASE_FACTOR))
        for (i in 0 until capacity) {
            while (true) {
                val value = array[i].value
                if (value != null && array[i].compareAndSet(value, MovedElement(value.element))) {
                    newCore.value!!.array[i].compareAndSet(null, value)
                }
            }
        }
        return newCore.value!!
    }
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME
private const val CAPACITY_INCREASE_FACTOR = 2