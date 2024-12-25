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

class DynamicArrayImpl<E : Any> : DynamicArray<E> {
    private val core = atomic(Core<E>(INITIAL_CAPACITY))

    override fun get(index: Int): E = core.value.get(index).value

    override fun put(index: Int, element: E) {
        val currentCore = core.value
        while (true) {
            val cell = currentCore.get(index)
            if (cell is Moved) continue
            if (currentCore.cas(index, cell, Based(element))) return
        }
    }

    override fun pushBack(element: E) {
        val currentCore = core.value
        while (true) {
            if (size > core.value.capacity) {
                move(currentCore)
                continue
            }
            if (currentCore.cas(size, null, Based(element))) {
                currentCore.faa()
                return
            }
        }
    }

    override val size: Int get() = core.value.size

    private fun move(currentCore: Core<E>) {
        currentCore.next.compareAndSet(null, Core(currentCore.capacity * 2))
        val nextCore = currentCore.next.value ?: throw IllegalArgumentException("Something wrong")
        for (i in 0 until currentCore.capacity) {
            var cell = currentCore.get(i)
            while (cell is Based) {
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
    private val _size = atomic(0)
    val next: AtomicRef<Core<E>?> = atomic(null)
    val size: Int get() = _size.value

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): Cell<E> {
        require(index < size)
        return array[index].value as Cell<E>
    }

    fun cas(index: Int, expected: Cell<E>?, actual: Cell<E>?): Boolean =
        array[index].compareAndSet(expected, actual)

    fun casSize(expected: Int, actual: Int): Boolean = _size.compareAndSet(expected, actual)

    fun faa(): Int = _size.getAndIncrement()
}

private open class Cell<E>(val value: E)
private class Based<E>(value: E) : Cell<E>(value)
private class Moved<E>(value: E) : Cell<E>(value)

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME