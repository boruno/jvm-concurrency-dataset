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
        return when (val curElement = core.value.get(index)) {
            is Cell.Normal -> {
                curElement.value
            }

            else -> {
                moveAllElements()
                get(index)
            }
        }
    }

    override fun put(index: Int, element: E) {
        when (val curElement = core.value.get(index)) {
            is Cell.Normal -> {
                core.value.put(index, Cell.Normal(element))
            }

            else -> {
                moveAllElements()
                put(index, element)
            }
        }
    }

    override fun pushBack(element: E) {
        while (true) {
            if (core.value.size >= core.value.capacity) {
                increaseCapacity()
                moveAllElements()
            } else {
                val curSize = core.value.size
                if (core.value._size.compareAndSet(curSize, curSize + 1)) {
                    put(curSize, element)
                }
            }
        }
    }

    override val size: Int get() = core.value.size

    private fun increaseCapacity() {
        val newCapacity = core.value.capacity * 2
        val newCore = Core<E>(newCapacity)
        core.value.setNext(newCore)
    }

    private fun moveAllElements() {
//        if (core.value.next.value == null) {
//            increaseCapacity()
//        }
        (0 until core.value.size).forEach { ind ->
            when (val element = core.value.get(ind)) {
                is Cell.Normal<*> -> {
                    core.value.put(ind, Cell.Fixed(element as Cell.Normal<E>))
                    // TODO do we need here?
                }

                is Cell.Fixed<*> -> {
                    core.value.next.value!!.put(ind, Cell.Normal(element.value))
                    core.value.put(ind, Cell.Broken(element as Cell.Fixed<E>))
                }

                is Cell.Broken<*> -> {}
            }
        }
        val curCore = core.value
        val newCore = core.value.next.value!!
        core.compareAndSet(curCore, newCore)
    }
}

private class Core<E>(
    val capacity: Int,
) {
    private val array = atomicArrayOfNulls<Cell<E>>(capacity)
    val _size = atomic(0)
    val next: AtomicRef<Core<E>?> = atomic(null)

    val size: Int = _size.value

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): Cell<E> {
        require(index < size)
        return array[index].value as Cell<E>
    }

    fun put(index: Int, element: Cell<E>) {
        require(index < size)
        do {
            val oldValue = get(index)
        } while (!array[index].compareAndSet(oldValue, element))
    }

    fun setNext(newNext: Core<E>) = next.compareAndSet(null, newNext)
}

private sealed class Cell<E>(val value: E) {
    class Broken<E>(element: Fixed<E>) : Cell<E>(element.value)
    class Fixed<E>(element: Normal<E>) : Cell<E>(element.value)
    class Normal<E>(newValue: E) : Cell<E>(newValue)
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME