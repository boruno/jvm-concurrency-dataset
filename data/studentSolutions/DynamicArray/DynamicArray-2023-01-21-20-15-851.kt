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
        core.value.set(index, element)
    }

    override fun pushBack(element: E) {
        while (true) {
            val oldCore = core.value
            if (!oldCore.canPushBack()) {
                oldCore.moveToNewCore()
                if (!core.compareAndSet(oldCore, oldCore.next)) {
                    continue
                }
            }

            val oldSize = oldCore.size
            if (!oldCore.casElement(oldSize, null, element)) {
                oldCore.compareAndIncSize(oldSize)
                continue
            }
            oldCore.compareAndIncSize(oldSize)
            /*if (core.value == oldCore) {
                return
            }*/
        }
    }

    override val size: Int get() = core.value.size
}

private class Core<E>(
    val capacity: Int,
) {
    private val labelArray = Array(capacity){ false } // when false -> value still in array, when true -> value moved
    private val array = atomicArrayOfNulls<E>(capacity)
    private val _size = atomic(0)
    private val newCore: AtomicRef<Core<E>?> = atomic(null)

    val size: Int = _size.value

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E {
        require(index < size)
        val got = array[index].value
        if (labelArray[index]) {
            return newCore.value!!.get(index)
        }
        return got as E
    }

    fun moveToNewCore() {
        if (newCore.compareAndSet(null, Core(2 * capacity))) {
            newCore.value!!._size.value = size
        }
        for (i in 0 until capacity) {
            labelArray[i] = true
            newCore.value!!.array[i].compareAndSet(null, array[i].value)
        }
    }

    fun canPushBack(): Boolean {
        return size < capacity
    }

    val next get() = newCore.value!!

    fun casElement(index: Int, expected: E?, update: E): Boolean {
        if (index >= capacity) {
            return false
        }
        return array[index].compareAndSet(expected, update)
    }

    fun compareAndIncSize(expected: Int): Boolean {
        return _size.compareAndSet(expected, expected + 1)
    }

    fun set(index: Int, element: E) {
        require(index < size)
        array[index].value = element
        if (labelArray[index]) {
            newCore.value!!.set(index, element)
        }
    }
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME