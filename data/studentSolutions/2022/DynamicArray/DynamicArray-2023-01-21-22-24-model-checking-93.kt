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
    private val _size = atomic(0)

    override fun get(index: Int): E {
        require(index < size)
        return core.value.get(index)
    }

    override fun put(index: Int, element: E) {
        require(index < size)
        core.value.set(index, element)
    }

    override fun pushBack(element: E) {
        while (true) {
            val oldCore = core.value
            val oldSize = _size.value
            if (oldSize >= oldCore.capacity) {
                oldCore.moveToNewCore()
                core.compareAndSet(oldCore, oldCore.next)
                continue
            }

            if (!oldCore.casElement(oldSize, null, element)) {
                _size.compareAndSet(oldSize, oldSize + 1)
                continue
            }
            _size.compareAndSet(oldSize, oldSize + 1)
            break
        }
    }

    override val size: Int get() = _size.value
}

private class Core<E>(
    val capacity: Int,
) {
    private val labelArray = Array(capacity){ false } // when false -> value still in array, when true -> value moved
    private val array = atomicArrayOfNulls<E>(capacity)
    private val newCore: AtomicRef<Core<E>?> = atomic(null)

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E {
        return array[index].value as E
    }

    fun moveToNewCore() {
        newCore.compareAndSet(null, Core(2 * capacity))
        for (i in 0 until capacity) {
            while (true) {
                labelArray[i] = true
                val oldValue = array[i].value
                newCore.value!!.casElement(i,null, oldValue)
                if (newCore.value!!.array[i].value != oldValue) {
                    newCore.value!!.casElement(i, oldValue, null)
                    continue
                }
                break
            }
        }
    }

    val next get() = newCore.value!!

    fun casElement(index: Int, expected: E?, update: E?): Boolean {
        if (index >= capacity) {
            return false
        }
        return array[index].compareAndSet(expected, update)
    }

    fun set(index: Int, element: E) {
        array[index].value = element
        if (labelArray[index]) {
            newCore.value!!.set(index, element)
        }
    }
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME