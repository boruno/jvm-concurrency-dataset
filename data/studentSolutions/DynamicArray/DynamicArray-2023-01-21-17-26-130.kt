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
    private val core = atomic(Core<E>(INITIAL_CAPACITY, 0))

    override fun get(index: Int): E = core.value.get(index)

    override fun put(index: Int, element: E) {
        require(index < size) { "Index $index is out of bounds for size $size" }

        var currentCore = core.value

        while (true) {
            val value = currentCore.array[index].value

            if (value == null) {
                enlarge(currentCore)
                currentCore.array[index].compareAndSet(null, Cell(State.STANDARD, element))
                break
            }

            if (value.state == State.S) {
                currentCore = currentCore.nextArray.value!!
                continue
            }

            if (value.state == State.MOD) {
                currentCore.nextArray.value!!.array[index].compareAndSet(null, Cell(State.STANDARD, element))
                currentCore.array[index].compareAndSet(value, Cell(State.S))
            } else if (value.state == State.STANDARD
                && currentCore.array[index].compareAndSet(value, Cell(State.STANDARD, element))) {
                break
            }
        }
    }

    override fun pushBack(element: E) {
        while (true) {
            val currentCore = core.value
            val size = currentCore.size
            if (size >= currentCore.capacity) {
                enlarge(currentCore) // help enlarge
                continue
            }
            if (currentCore.array[size].compareAndSet(null, Cell(State.STANDARD, element))) {
                currentCore.setSize(size)
                break
            }
        }
    }

    private fun enlarge(currentCore: Core<E>) {
        currentCore.nextArray.compareAndSet(null, Core(currentCore.capacity * 2, size))

        currentCore.nextArray.value?.let {
            for(index in 0 until currentCore.size) {
                while (true) {
                    val value = currentCore.array[index].value ?: continue

                    if (value.state == State.S) {
                        break
                    }

                    if (value.state == State.MOD) {
                        it.array[index].compareAndSet(null, Cell(State.STANDARD, value.value))
                        currentCore.array[index].compareAndSet(value, Cell(State.S))
                    } else if (value.state == State.STANDARD) {
                        currentCore.array[index].compareAndSet(value, Cell(State.MOD, value.value))
                    }
                }
            }
            core.compareAndSet(currentCore, it)
        }
    }

    override val size: Int get() = core.value.size
}

private class Core<E>(val capacity: Int, previousSize: Int) {
    val array = atomicArrayOfNulls<Cell<E>>(capacity)
    val nextArray = atomic(null as Core<E>?)
    private val _size = atomic(previousSize)

    val size: Int get() = _size.value

    fun setSize(value: Int) {
        _size.compareAndSet(value, value + 1)
    }

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E {
        require(index < size) { "Index $index is out of bounds for size $size" }
        array[index].value?.let {
            when (it.state) {
                State.STANDARD -> return it.value as E
                State.MOD -> return it.value as E
                State.S -> return nextArray.value?.get(index) ?: throw RuntimeException(
                    "Unreachable: because of previous check."
                )
            }
        } ?: throw RuntimeException("Unreachable: array[index] is null.")
    }
}

private class Cell<T>(val state: State, val value: T? = null)

enum class State {
    STANDARD, MOD, S
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME