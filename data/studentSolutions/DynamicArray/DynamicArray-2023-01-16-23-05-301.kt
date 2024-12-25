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
    private val elementsCounter = atomic(0)
    override val size: Int get() = elementsCounter.value

    override fun get(index: Int): E {
        require(index < size)
        while (true) {
            val core = core.value
            val value = core.array.get(index).value
            if (!moveIfNeeded(core, value)) {
                if (value is Stationary<*>) {
                    return value.value as E
                }
            }
        }
    }

    override fun put(index: Int, element: E) {
        require(index < size)
        while (true) {
            val core = core.value
            val value = core.array.get(index).value
            if (!moveIfNeeded(core, value)) {
                if (setStationary(index, core, value, element)) {
                    return
                }
            }
        }
    }

    private fun move(core: Core<E>) {
        if (core.next.value == null) {
            core.next.compareAndSet(null, Core(2 * core.capacity))
        }
        val next = core.next.value
        if (next == null) {
            return
        }
        for (i in 0 until core.capacity) {
            var value = core.array.get(i).value
            while (value is Stationary<*> && !core.array.get(i).compareAndSet(value, Movable(value.value))) {
                value = core.array.get(i).value
            }
            if (value is Movable<*>) {
                setStationary(i, next, null, value.value)
            }
            if (value is Stationary<*>) {
                setStationary(i, next, null, value.value)
            }
        }
        this.core.compareAndSet(core, next)
    }

    override fun pushBack(element: E) {
        while (true) {
            val size = size
            val core = core.value
            if (size >= core.capacity) {
                move(core)
            } else if (setStationary(size, core, null, element)) {
                elementsCounter.incrementAndGet()
                return
            }
        }
    }
    private fun setStationary(num : Int, core: Core<E>, expect: Any?, value: Any?): Boolean {
        return core.array.get(num).compareAndSet(expect, Stationary(value))
    }

    private fun moveIfNeeded(core: Core<E>, value: Any?): Boolean {
        if (value is Movable<*>) {
            move(core)
            return true
        } else {
            return false
        }
    }
        
}

private class Core<E>(
    val capacity: Int,
) {
    val next: AtomicRef<Core<E>?> = atomic(null)
    val array = atomicArrayOfNulls<Any>(capacity)
    private val _size = atomic(0)

    val size: Int = _size.value

    fun get(index: Int): Any? {
        return array.get(index).value
    }

    fun set(index: Int, expect: Any?, update: Any?): Boolean {
        return array.get(index).compareAndSet(expect, update)
    }
}

private class Movable<E> {
    val value: E

    constructor(value: E) {
        this.value = value
    }
}

private class Stationary<E> {
    val value: E

    constructor(value: E) {
        this.value = value
    }
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME