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
    private val next: AtomicRef<Core<E>?> = atomic(null)

    override fun get(index: Int): E {
        if (index < 0 || index >= size) {
            throw IllegalArgumentException("Index out of bounds")
        }
        return core.value.get(index).element
    }

    override fun put(index: Int, element: E) {
        val elem = Core.Wrap(element)
        if (index < 0 || index >= size) {
            throw IllegalArgumentException("Index out of bounds")
        }
        while (true) {
            val curCore = core.value
            val curElem = curCore.get(index)
            if (curElem is Core.Removed<*>) {
                if (curCore.array[index].compareAndSet(curElem, elem)) return
            }
        }
    }

    override fun pushBack(element: E) {
        val elem = Core.Wrap(element)
        while (true) {
            val curCore = core.value
            if (size == curCore.capacity) {
                val newCore = Core<E>(curCore.capacity * 2, size)
                if (next.compareAndSet(null, newCore)) {
                    for (i in 0 until size) {
                        while (true) {
                            val value = curCore.get(i)
                            val removed = Core.Removed(value)
                            if (curCore.array[i].compareAndSet(value, removed)) {
                                newCore.array[i].compareAndSet(null, Core.Wrap(value.element))
                                break
                            }
                        }
                    }
                    core.compareAndSet(curCore, newCore)
                    next.compareAndSet(newCore, null)
                }
            } else {
                if (curCore.array[size].compareAndSet(null, elem)) {
                    curCore._size.incrementAndGet()     // may fail if we've been helped
                    return
                }
            }
        }
    }

    override val size: Int get() = core.value.size
}

private class Core<E>(
    val capacity: Int,
    val startSize: Int = 0
) {
    val array = atomicArrayOfNulls<Wrap<E>>(capacity)
    val _size = atomic(startSize)

    val size: Int = _size.value

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): Wrap<E> {
        require(index < size)
        return array[index].value as Wrap<E>
    }

    open class Wrap<E> (val element : E) {
        constructor(node : Wrap<E>) : this(node.element)
    }
    class Removed<E> (node : Wrap<E>) : Wrap<E>(node)
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME