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
        core.value.put(index, element)
    }

    override fun pushBack(element: E) {
        while (true) {
            val curCore = core.value
            if (curCore.pushBack(element)) return
            core.compareAndSet(curCore, curCore.grow())
        }
    }

    override val size: Int get() = core.value.size
}

private class Core<E> private constructor(capacity: Int, initialSize: Int = 0) {
    private val array = atomicArrayOfNulls<Any?>(capacity)
    private val _size = atomic(0)
    private val _next = atomic<Core<E>?>(null)

    val size: Int
        get() = _size.value
    val next: Core<E>
        get() = _next.value ?: error("Next core is not initialized")

    constructor(capacity: Int) : this(capacity, 0)

    fun get(index: Int): E {
        require(index < size) { "Index $index is out of bounds for size $size" }

        array[index].loop {
            when (val element = array[index].value) {
                null -> error("Element at index $index is null")
                is Fixed<*> -> move(index)
                is Moved -> return next.get(index)
                else -> @Suppress("UNCHECKED_CAST") return element as E
            }
        }
    }

    fun put(index: Int, element: E) {
        require(index < size) { "Index $index is out of bounds for size $size" }

        array[index].loop {
            when (val curElement = array[index].value) {
                null -> error("Element at index $index is null")
                is Fixed<*> -> move(index)
                is Moved -> next.put(index, element)
                else -> if (array[index].compareAndSet(curElement, element)) return
            }
        }
    }

    fun pushBack(element: E): Boolean {
        while (true) {
            val index = _size.value

            assert(index <= array.size) { "Index $index is out of bounds for size ${array.size}" }

            if (index == array.size) return false
            if (array[index].compareAndSet(null, element)) {
                _size.compareAndSet(index, index + 1)
                return true
            }
            _size.compareAndSet(index, index + 1)
        }
    }

    fun grow(): Core<E> {
        assert(_size.value == array.size) { "Size ${_size.value} is not equal to capacity ${array.size}" }

        _next.compareAndSet(null, Core(array.size * 2, size))
        (0 until size).forEach { move(it) }
        return next
    }

    private fun move(index: Int) {
        array[index].loop {
            when (it) {
                null -> error("Element at index $index is null")
                is Fixed<*> -> {
                    next.array[index].compareAndSet(null, it.element)
                    array[index].value = Moved
                }
                is Moved -> return
                else -> array[index].compareAndSet(it, Fixed(index))
            }
        }
    }
}

private class Fixed<E>(val element: E)

private object Moved

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME