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
        require(index in 0 until size)
        return core.value.put(index, element)
    }

    override fun pushBack(element: E) {
        val expectedCore = core.value
        while (true) {
            val result = core.value.pushBack(element, size)
            if (result == SUCCESS) break
            if (result == NEED_TO_RESIZE) resize(expectedCore)
        }
    }

    override val size: Int get() = core.value.size

    fun resize(expectedCore: Core<E>) {
        core.compareAndSet(expectedCore, core.value.resize())
    }
}

class Core<E>(
    capacity: Int,
) {
    private val array = atomicArrayOfNulls<E>(capacity)
    private val _size = atomic(0)

    private val next: AtomicRef<Core<E>?> = atomic(null)


    val size: Int = _size.value

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E {
        require(index in 0 until size)
        return array[index].value as E
    }

    @Suppress("UNCHECKED_CAST")
    fun put(index: Int, element: E) {
        require(index in 0 until size)
        array[index].compareAndSet(get(index), element)
    }

    @Suppress("UNCHECKED_CAST")
    fun pushBack(element: E, expectedSize: Int): Int {
        if (size == array.size) {
            return NEED_TO_RESIZE
        }
        if (_size.compareAndSet(expectedSize, expectedSize + 1)) {
            array[expectedSize].compareAndSet(null, element)
            return SUCCESS
        }
        return NOT_SUCCESS
    }

    fun resize(): Core<E> {
        next.compareAndSet(null, Core(2 * array.size))
        for (i in 0 until array.size) next.value!!.put(i, array[i].value!!)
        return next.value!!
    }
}

private const val NEED_TO_RESIZE = 1
private const val SUCCESS = 2
private const val NOT_SUCCESS = 3
private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME