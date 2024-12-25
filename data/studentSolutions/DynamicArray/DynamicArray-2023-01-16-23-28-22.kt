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
        if (index >= size) throw IllegalArgumentException()
        while (true) {
            val coreValue = core.value
            val elemByIndex = coreValue.get(index)
            if (!moveIfNeeded(coreValue, elemByIndex) && elemByIndex is Stationary<*>) {
                return elemByIndex.inner as E
            }    
        }
    }

    override fun put(index: Int, element: E) {
        if (index >= size) throw IllegalArgumentException()
        while (true) {
            val coreValue = core.value
            val valueByIndex = coreValue.get(index)
            if (!moveIfNeeded(coreValue, valueByIndex)) {
                if (coreValue.set(index, valueByIndex, Stationary(element))) {
                    return
                }
            }
        }
        // TODO("Not yet implemented")
    }

    private fun moveIfNeeded(coreValue: Core<E>, value: Any?): Boolean {
        if (value is Movable<*>) {
            move(coreValue)
            return true
        } else {
            return false
        }
    }

    private fun move(core: Core<E>) {
        core.next.value ?: core.next.compareAndSet(null, Core(2 * core.capacity))
        val next = core.next.value ?: return
        (0 until core.capacity).forEach { num ->
            var value: Any?

            do value = core.array[num].value
            while (value is Stationary<*> && !core.array[num].compareAndSet(value, Movable(value.inner)))

            if (value is Movable<*>) next.set(num, null, Stationary(value.inner))
            if (value is Stationary<*>) next.set(num, null, Stationary(value.inner))
        }
        this.core.compareAndSet(core, next)
    }

    override fun pushBack(element: E) {
        while (true) {
            val coreValue = core.value
            val sizeCopy = size
            if (sizeCopy >= coreValue.capacity) {
                move(coreValue)
            } else if (coreValue.set(sizeCopy, null, Stationary(element))) {
                elementsCounter.incrementAndGet()
                return
            }
        }
        // TODO("Not yet implemented")
    }

    override val size: Int get() = core.value.size
    private val elementsCounter = atomic(0)
}

private class Core<E>(
    val capacity: Int,
) {
    val next: AtomicRef<Core<E>?> = atomic(null)
    val array = atomicArrayOfNulls<Any>(capacity)
    private val _size = atomic(0)

    val size: Int = _size.value

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): Any? {
        return array[index].value
    }

    fun set(index: Int, expect: Any?, update: Any?): Boolean {
        return array[index].compareAndSet(expect, update)
    }
}

private class Movable<E> {
    val inner: E

    constructor(inner: E) {
        this.inner = inner
    }
}

private class Stationary<E> {
    val inner: E

    constructor(inner: E) {
        this.inner = inner
    }
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME