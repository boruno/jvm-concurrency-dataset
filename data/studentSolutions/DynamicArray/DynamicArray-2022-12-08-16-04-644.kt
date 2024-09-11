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

    override fun put(index: Int, element: E) = core.value.put(index, element)

    override fun pushBack(element: E) {
        val expectedCore = core.value
        while (true) {
            val result = core.value.pushBack(element, size)
            if (result == SUCCESS) break
            if (result == RESIZED) core.compareAndSet(expectedCore, core.value.next.value!!)
        }
    }

    override val size: Int get() = core.value.size
}

class Core<E>(
    capacity: Int,
) {
    private val array = atomicArrayOfNulls<E>(capacity)
    private val _size = atomic(0)

    val next: AtomicRef<Core<E>?> = atomic(null)


    val size: Int get() = _size.value

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E {
        require(index in 0 until size)
        return array[index].value as E
    }


    fun put(index: Int, element: E) {
        require(index in 0 until size)
        array[index].compareAndSet(get(index), element)
    }


    fun pushBack(element: E, expectedSize: Int): Int {
        if (size == array.size) {
            next.compareAndSet(null, Core(2 * array.size))
            while (next.value!!.array[next.value!!.size - 1].value == null) {
                for (i in 0 until array.size) {
                    if (next.value!!._size.compareAndSet(i, i + 1)) {
                        next.value!!.array[i].compareAndSet(null, array[i].value!!)
                    }
                }
            }
            return RESIZED
        }
        if (_size.compareAndSet(expectedSize, expectedSize + 1)) {
            array[expectedSize].compareAndSet(null, element)
            return SUCCESS
        }
        return NOT_SUCCESS
    }
}

private const val RESIZED = 1
private const val SUCCESS = 2
private const val NOT_SUCCESS = 3
private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME