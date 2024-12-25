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

    override fun put(index: Int, element: E) = core.value.put(index, element)

    override fun pushBack(element: E) {
        while (true) {
            val core = core.value
            if (core.pushBack(element)) {
                return
            }
            val nextCore = core.copyCore()
            this.core.compareAndSet(core, nextCore)
        }
    }

    override val size: Int get() = core.value.size
}

private class Core<E>(
    val capacity: Int
) {
    private val array = atomicArrayOfNulls<E>(capacity)
    private val _size = atomic(0)
    private val nextCore: AtomicRef<Core<E>?> = atomic(null)

    val size: Int = _size.value

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E {
        require(index < size)
        return array[index].value as E
    }

    fun put(index: Int, element: E) {
        require(index < size)
        var core: Core<E>? = this
        while (core != null) {
            core.array[index].value = element
            core = core.nextCore.value
        }
    }

    fun pushBack(element: E): Boolean {
        while (true) {
            val curSize = size
            if (curSize < capacity) {
                if (array[curSize].compareAndSet(null, element)) {
                    _size.incrementAndGet()
                    return true // done
                }
                continue // race
            }
            return false
        }
    }

    fun copyCore(): Core<E> {
        // threads help each other with building next core
        nextCore.compareAndSet(null, Core(capacity * 2))
        val nextCore = nextCore.value!!
        repeat(capacity) { i ->
            nextCore.array[i].compareAndSet(null, this.array[i].value)
        }
        nextCore._size.compareAndSet(0, size)
        return nextCore
    }
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME