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
    private val lock = Lock()

    override fun get(index: Int): E {
        if (index >= size) {
            throw IllegalArgumentException()
        }
        return core.value.get(index)
    }

    override fun put(index: Int, element: E) {
        if (index >= size) {
            throw IllegalArgumentException()
        }

        while (!lock.tryLock()) {}
        core.value.put(index, element)
        lock.unlock()
    }

    override fun pushBack(element: E) {
        while (!lock.tryLock()) {}

        if (core.value.size == core.value.capacity) {
            var new_core = Core<E>(core.value.capacity * 2)
            for (i in 0 until core.value.size) {
                new_core.put(i, core.value.get(i))
            }
            core.value = new_core
            return
        }
        else {
            core.value.push(element)
            return
        }
    }

    override val size: Int get() = core.value.size
}

private class Lock(){
    private val lock = atomic(false)
    fun tryLock(): Boolean {
        return lock.compareAndSet(false, true)
    }
    fun unlock(): Boolean {
        return lock.compareAndSet(true, false)
    }
    fun getLock(): Boolean {
        return lock.value
    }
}

private class Core<E>(
    capacity: Int,
) {
    private val array = atomicArrayOfNulls<E>(capacity)
    private val _size = atomic(0)

    val size: Int = _size.value
    val capacity: Int = array.size

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E {
        require(index < size)
        return array[index].value as E
    }

    fun put(index: Int, element: E): Boolean {
        require(index < size)
        return array[index].compareAndSet(array[index].value, element)
    }

    fun push(element: E): Boolean {
        require(size < capacity)
        if (array[size].compareAndSet(null, element)) {
            if (_size.compareAndSet(size, size + 1)) {
                return true
            }
            else {
                array[size].compareAndSet(element, null)
                return false
            }
        }
        else {
            return false
        }
    }
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME