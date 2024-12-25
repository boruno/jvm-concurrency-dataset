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

    override fun get(index: Int): E {
        if (index < 0 || index >= size) {
            throw IllegalArgumentException("Index out of bounds")
        }
        while (true) {
            if (core.value.capacity > index) {
                val value = core.value.array.get(index).value
                if (value == null) {
                    continue
                }
                return value
            }
        }
    }

    override fun put(index: Int, element: E) {
        if (index < 0 || index >= size) {
            throw IllegalArgumentException("Index out of bounds")
        }
        while (true) {
            val coreValue = core.value
            if (coreValue.capacity > index) {
                if (coreValue.array.get(index).getAndSet(null) == null) {
                    continue
                }
                coreValue.array.get(index).value = element
                return
            }
        }
    }

    override fun pushBack(element: E) {
        val incrementedSize = elementsCounter.getAndIncrement()
        while (true) {
            val value = core.value
            val oldCapacity = value.capacity
            if (oldCapacity <= incrementedSize) {
                val newCapacity = oldCapacity + oldCapacity
                val newCore = Core<E>(newCapacity)
                if (!value.next.compareAndSet(null, newCore)) {
                    continue
                }
                for (i in 0 until oldCapacity) {
                    while (true) {
                        val oldElem = value.array.get(i).getAndSet(null)
                        if (oldElem != null) {
                            newCore.array.get(i).value = oldElem
                            break
                        }
                    }
                }
                this.core.value = newCore
            } else {
                if (!value.array.get(incrementedSize).compareAndSet(null, element)) {
                    continue
                } else {
                    return
                }
            }
        }
    }
    override val size get() = elementsCounter.value
}

private class Core<E>(val capacity: Int) {
    val next: AtomicRef<Core<E>?> = atomic(null)
    val array = atomicArrayOfNulls<E>(capacity)
    private val _size = atomic(0)

    val size: Int = _size.value

    fun get(index: Int): Any? {
        return array.get(index).value
    }
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME