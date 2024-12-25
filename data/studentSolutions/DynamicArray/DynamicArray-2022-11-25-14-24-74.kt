//package mpp.dynamicarray

import kotlinx.atomicfu.*
import java.lang.IllegalStateException

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
    private val core = atomic(Core<Cell<E>>(INITIAL_CAPACITY))

    override fun get(index: Int): E = core.value.get(index).value

    override fun put(index: Int, element: E) {
        while (true) {
            val curCore = core.value
            val curCell = curCore.get(index)

            when (curCell.state) {
                State.COMMON -> if (curCore.cas(index, curCell, Cell(element, State.COMMON))) {
                    return
                }
                State.FIXED -> helpMove()
                State.MOVED -> continue
            }
        }
    }

    override fun pushBack(element: E) {
        while (true) {
            val curCore = core.value
            val curSize = curCore.size.value

            if (curSize < curCore.capacity) {
                if (curCore.cas(curSize, null, Cell(element, State.COMMON))) {
                    curCore.size.incrementAndGet()
                    return
                }
            } else {
                val newCore = Core<Cell<E>>(curCore.capacity shl 1)
                newCore.size.value = curSize
                helpMove()
            }
        }
    }

    override val size: Int get() = core.value.size.value

    private fun helpMove() {
        val curCore = core.value
        val nextCore = curCore.next.value ?: return

        for (i in 0 until curCore.capacity) {
            do {
                val curCell = curCore.get(i)
            } while (curCell.state == State.COMMON && !curCore.cas(i, curCell, Cell(curCell.value, State.FIXED)))

            val curCell = curCore.get(i)

            when (curCell.state) {
                State.COMMON -> throw IllegalStateException("cell cannot be in COMMON state here")
                State.FIXED -> {
                    nextCore.cas(i, curCell, Cell(curCell.value, State.COMMON))
                    curCore.cas(i, curCell, Cell(curCell.value, State.MOVED))
                }
                State.MOVED -> continue
            }
        }

        core.compareAndSet(curCore, nextCore)
    }
}

private class Core<E>(val capacity: Int) {
    private val array = atomicArrayOfNulls<E>(capacity)

    val next: AtomicRef<Core<E>?> = atomic(null)
    val size = atomic(0)

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E {
        require(index < size.value)
        return array[index].value as E
    }

    fun cas(index: Int, expect: E?, update: E): Boolean {
        return array[index].compareAndSet(expect, update)
    }
}

private enum class State {
    COMMON, FIXED, MOVED,
}

private class Cell<E>(val value: E, val state: State)

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME