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

    override fun get(index: Int): E {
        updateCore()
        return _get(index, core.value)
    }

    private fun _get(index: Int, localCore: Core<E>): E {
        while (true) {
            if (localCore.next.value != null) {
                val result = localCore.next.value!!.array[index].value
                if (result != null) return result
            }
            val result = localCore.array[index].value
            if (result != null) return result
            if (localCore.discarded) return _get(index, localCore.next.value!!)
        }
    }

    override fun put(index: Int, element: E) {
        updateCore()
        _put(index, element, core.value)
    }

    private fun _put(index: Int, element: E, localCore: Core<E>) {
        while (true) {
            if (localCore.next.value != null) {
                return _put(index, element, localCore.next.value!!)
            }
            if ((index < 0) || (localCore.size.value <= index)) {
                throw IllegalArgumentException("Index is out of bounds")
            }
            val oldVal = localCore.array[index].value
            if (oldVal == null) continue
            if (localCore.array[index].compareAndSet(oldVal, element)) return
        }
    }

    override fun pushBack(element: E) {
        _pushBack(element, core.value)
    }

    private fun _pushBack(element: E, localCore: Core<E>) {
        while (true) {
            val index = localCore.size.value
            if (index < localCore.capacity) {
                if (localCore.array[index].compareAndSet(null, element)) {
                    localCore.size.compareAndSet(index, index + 1)
                    return
                }
                localCore.size.compareAndSet(index, index + 1)
                continue
            }
            val localNextCore = Core<E>(2 * localCore.capacity)
            localNextCore.size.value = localCore.capacity + 1 // account for the element the pushback is putting
            if (!localCore.next.compareAndSet(null, localNextCore)) {
                return _pushBack(element, localCore.next.value!!)
            }
            localNextCore.array[index].compareAndSet(null, element)
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
        if (core.value.discarded == true) {
            core.value = core.value.next.value!!
        }
    }

    override val size: Int get() = core.value.size.value
}

private class Core<E>(
    val capacity: Int,
) {
    val array = atomicArrayOfNulls<E>(capacity)
    val size = atomic(0)
    val next = atomic<Core<E>?>(null)
    var discarded = false
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME