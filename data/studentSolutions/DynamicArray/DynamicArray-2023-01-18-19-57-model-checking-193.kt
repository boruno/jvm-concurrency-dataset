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
    private val _size = atomic(0)

    override fun get(index: Int): E {
        require(index < _size.value)
        return core.value.get(index).value
    }

    override fun put(index: Int, element: E) {
        require(index < _size.value)
        while (true) {
            val currentCore = core.value
            val cell = currentCore.get(index)
            if (cell is Moved) {
                move(currentCore)
                continue
            }
            if (currentCore.cas(index, cell, Based(element))) return
        }
    }

    override fun pushBack(element: E) {
        while (true) {
            val currentCore = core.value
            val currentSize = _size.value
            if (currentSize >= currentCore.capacity) {
                move(currentCore)
                continue
            }
            _size.getAndIncrement()
            if (currentCore.cas(currentSize, null, Based(element))) {
                return
            }
        }
    }

    override val size: Int get() = _size.value

    private fun move(currentCore: Core<E>) {
        currentCore.next.compareAndSet(null, Core(currentCore.capacity * 2))
        val nextCore = currentCore.next.value ?: throw IllegalArgumentException("Something wrong")
        for (i in 0 until currentCore.capacity) {
            var cell = currentCore.get(i)
            while (true) {
                if (cell is Moved) break
                if (currentCore.cas(i, cell, Moved(cell.value))) break
                cell = currentCore.get(i)
            }
            nextCore.cas(i, null, Based(cell.value))
        }
        core.compareAndSet(currentCore, nextCore)
    }
}

private class Core<E>(
    val capacity: Int,
) {
    private val array = atomicArrayOfNulls<Cell<E>>(capacity)
    val next: AtomicRef<Core<E>?> = atomic(null)

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): Cell<E> = array[index].value as Cell<E>

    fun cas(index: Int, expected: Cell<E>?, actual: Cell<E>?): Boolean = array[index].compareAndSet(expected, actual)
}

private open class Cell<E>(val value: E)
private class Based<E>(value: E) : Cell<E>(value)
private class Moved<E>(value: E) : Cell<E>(value)

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME