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

    override fun get(index: Int): E = core.value.get(index)

    override fun put(index: Int, element: E) {
        checkIndexBound(index).onFailure { throw it }
        while (true) {
            val coreValue = core.value
            if (coreValue.array.size > index) {
                coreValue.array[index].getAndSet(null) ?: continue
                coreValue.array[index].value = element
                return
            }
        }   
    }

    override fun pushBack(element: E) {
        val newSize = size.inc()
        println("fff " + newSize)
        while (true) {
            val coreValue = core.value
            if (newSize < coreValue.array.size) {
                if (coreValue.array[newSize].compareAndSet(null, element)) return
            } else resize(coreValue)
        }
    }

    override val size: Int get() = core.value.size

    private fun resize(oldCore: Core<E>) {
        val newCore = Core<E>(oldCore.array.size + oldCore.array.size)

        if (oldCore.nxt.compareAndSet(null, newCore)) {
            for (i in 0 until oldCore.array.size) {
                while (true) {
                    val el = oldCore.array[i].getAndSet(null)
                    if (el != null) {
                        newCore.array[i].value = el
                        break
                    }
                }
            }
            this.core.value = newCore
        }
    }

    private fun checkIndexBound(index: Int): Result<Unit> =
        if (index >= size || index < 0) Result.failure(IllegalArgumentException("Index out of bound exception"))
        else Result.success(Unit)
}

private class Core<E>(
    capacity: Int,
) {
    val array = atomicArrayOfNulls<E>(capacity)
    private val _size = atomic(0)

    val size: Int = _size.value
    val nxt = atomic<Core<E>?>(null)

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E {
        require(index < size)
        return array[index].value as E
    }
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME