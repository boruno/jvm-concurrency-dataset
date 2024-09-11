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
        if ((index < 0) || (size <= index)) {
            throw IllegalArgumentException("Index is out of bounds")
        }
        updateCore()
        var localCore = core.value
        while (true) {
            if (index < localCore.capacity) {
                val result = localCore.array[index].value
                if (result != null) return result
            }
            if (localCore.next.value != null) {
                localCore = localCore.next.value!!
            }
        }
    }

    override fun put(index: Int, element: E) {
        if ((index < 0) || (size <= index)) {
            throw IllegalArgumentException("Index is out of bounds")
        }
        updateCore()
        var localCore = core.value
        while (true) {
            if (localCore.next.value != null) {
                localCore = localCore.next.value!!
                continue
            }
            val oldValue = localCore.array[index].value
            // if (oldVal == null) continue
            if (localCore.array[index].compareAndSet(oldValue, element)) return
        }
    }


    override fun pushBack(element: E) {
        var localCore = core.value
        while (true) {
            val index = size
            if (index < localCore.capacity) {
                if (localCore.array[index].compareAndSet(null, element)) {
                    return
                }
                continue
            }
            val localNextCore = Core<E>(2 * localCore.capacity)
            if (!localCore.next.compareAndSet(null, localNextCore)) {
                localCore = localCore.next.value!!
                continue
            }
            for (i in (0 until localCore.capacity)) {
                val value = localCore.array[i].value
                localNextCore.array[i].compareAndSet(null, value)
                localCore.array[i].value = null
            }
            localCore.discarded = true
            updateCore()
            return
        }
    }

    private fun updateCore() {
        var coreValue = core.value
        var nextCoreValue = coreValue.next.value
        while (coreValue.discarded && nextCoreValue != null) {
            if (!core.compareAndSet(coreValue, nextCoreValue)) break
            coreValue = nextCoreValue
            nextCoreValue = coreValue.next.value
        }
    }

    override val size: Int get() {
        updateCore()
        var localCore = core.value
        while (localCore.next.value != null) {
            localCore = localCore.next.value!!
        }
        var result = 0
        while (localCore.array[result].value != null) {
            result = result + 1
        }
        return result
        // var curSize = _size.value
        // while (curSize < localCore.capacity && localCore.array[curSize].value != null) {
        //     _size.compareAndSet(curSize, curSize + 1)
        //     curSize = _size.value
        // }
        // return _size.value
    }
}

private class Core<E>(
    val capacity: Int,
) {
    val array = atomicArrayOfNulls<E>(capacity)
    val next = atomic<Core<E>?>(null)
    var discarded = false
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME