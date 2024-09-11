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
        require(index < size) { "Index $index is out of bounds for size $size" }
        while (true) {
            var currentCore = core.value
            if (currentCore.next.value !== null) currentCore = currentCore.next.value!!
            currentCore.cas(index, currentCore.get(index), element)
            return
        }
    }

    override fun pushBack(element: E) {
        while (true) {
            val currentCore = core.value
            if (size < currentCore.capacity) {
                currentCore.faa()
                if (currentCore.cas(size, null, element)) return else continue
            } else {
                val nextCore = Core<E>(currentCore.capacity * 2)
                nextCore.casSize(0, size)
                currentCore.next.compareAndSet(null, nextCore)
                for (i in 0 until size) nextCore.cas(i, null, currentCore.get(i))
                core.compareAndSet(currentCore, currentCore.next.value!!)
            }
        }
    }

    override val size: Int get() = core.value.size
}

private class Core<E>(
    val capacity: Int,
) {
    private val array = atomicArrayOfNulls<E>(capacity)
    private val _size = atomic(0)
    val next: AtomicRef<Core<E>?> = atomic(null)
    val size: Int get() = _size.value

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E {
        require(index < size)
        return array[index].value as E
    }

    fun cas(index: Int, expected: E?, actual: E?): Boolean = array[index].compareAndSet(expected, actual)

    fun casSize(expected: Int, actual: Int): Boolean = _size.compareAndSet(expected, actual)

    fun faa(): Int = _size.getAndIncrement()
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME