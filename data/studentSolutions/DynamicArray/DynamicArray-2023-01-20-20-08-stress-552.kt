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
        while (true) {
            if (core.value.size > index) {
                return core.value.array[index].value ?: continue
            }
        }
    }

    override fun put(index: Int, element: E) {
        require(index < size)
        while (true) {
            if (core.value.size > index &&
                core.value.array[index].getAndSet(null) != null
            ) {
                core.value.array[index].value = element
                return
            }
        }
    }

    override fun pushBack(element: E) {
        val newSize = _size.getAndIncrement()
        while (true) {
            val coreValue = this.core.value
            if (ensureCapacity(newSize, coreValue)) continue
            if (coreValue.array[newSize].compareAndSet(null, element)) return
        }
    }

    private fun ensureCapacity(newSize: Int, oldCore: Core<E>): Boolean {
        if (newSize < oldCore.size) return false
        val core = Core<E>(oldCore.size * 2)
        if (oldCore.next.compareAndSet(null, core)) {
            for (i in 0 until oldCore.size) {
                while (true) {
                    val element = oldCore.array[i].getAndSet(null)
                    if (element != null) {
                        core.array[i].value = element
                        break
                    }
                }
            }
            this.core.value = core
        }
        return true
    }

    override val size: Int
        get() {
            return _size.value
        }
}

private class Core<E>(
    capacity: Int,
) {
    val array = atomicArrayOfNulls<E>(capacity)
    val _size = atomic(0)
    val next: AtomicRef<Core<E>?> = atomic(null)

    val size: Int = _size.value

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E {
        require(index < size)
        return array[index].value as E
    }
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME
