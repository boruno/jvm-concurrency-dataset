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
        while (!core.value.push(element)) {
            resetCore()
        }
    }

    private fun resetCore() {
        val currentCore = core.value
        if (currentCore.size >= currentCore.capacity) {
            val newCore = currentCore.new()
            if (!core.compareAndSet(currentCore, newCore)) {
                resetCore()
            }
        }
    }

    override val size: Int get() = core.value.size
}

private class Core<E>(
    capacity: Int,
) {
    private val array = atomicArrayOfNulls<E>(capacity)
    private val _size = atomic(0)

    val size: Int get() = _size.value
    val capacity: Int get() = array.size

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E {
        require(index < size)
        return array[index].value as E
    }

    fun CAS(index: Int, old: E?, new: E?): Boolean {
        require(index < capacity)
        return array[index].compareAndSet(old, new)
    }

    fun push(new: E?): Boolean {
        var index = size
        while (index < capacity) {
            if (CAS(index, null, new)) {
                _size.incrementAndGet()
                return true
            }
            ++index
        }
        _size.value = capacity
        return false
    }

    fun put(index: Int, element: E?): Boolean {
        require(index < size)
        val value: E? = array[index].value ?: return false
        return CAS(index, value, element)
    }

    fun new(): Core<E> {
        val new = Core<E>(capacity * 2)
        for (i in 0 until capacity) {
            if (array[i].value == null) {
                new._size.value = i
                break
            }
            new.array[i].value = array[i].value
        }
        return new
    }

}


private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME