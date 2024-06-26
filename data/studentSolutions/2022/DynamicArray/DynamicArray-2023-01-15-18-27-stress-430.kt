package mpp.dynamicarray

import kotlinx.atomicfu.*
import java.security.InvalidKeyException

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

class DynamicArrayImpl<E: Any> : DynamicArray<E> {
    private val core = atomic(Core<E>(INITIAL_CAPACITY))

    override fun get(index: Int): E = core.value.get(index)

    override fun put(index: Int, element: E) {
        core.value.put(index, Current(element))
    }

    override fun pushBack(element: E) {
        val currCore = core.value
        if(currCore.pushBack(element)) {
            return
        }
        currCore.resize()
        core.compareAndSet(currCore, core.value.nextCore.value!!)
    }

    override val size: Int get() = core.value.size
}

private class Core<E : Any>(
    capacity: Int,
) {
    private val array = atomicArrayOfNulls<Cell>(capacity)
    private val _size = atomic(0)
    val nextCore: AtomicRef<Core<E>?> = atomic(null)
    val size: Int get() { return _size.value }

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E {
        require(index < size)
        return when(val cell = array[index].value) {
            is Current -> cell.x as E
            is Moved -> this.nextCore.value!!.get(index)
            null -> throw InvalidKeyException()
        }
    }

    fun put(index: Int, element: Cell) {
        require(index < size)
        while (true) {
            when(val cell = array[index].value) {
                is Moved -> this.nextCore.value!!.put(index, element)
                else -> {
                    if(array[index].compareAndSet(cell, Current(element))) {
                        return
                    } else {
                        continue
                    }
                }
            }
        }
    }

    fun pushBack(element: E): Boolean {
        while (true) {
            val currSize = _size.value
            if(size == array.size) {
                return false
            }
            if(_size.compareAndSet(currSize, currSize + 1)) {
                array[currSize].compareAndSet(null, Current(element))
                return true
            }
        }
    }
    fun resize() {
        val newCore = Core<E>(array.size * 2)
        if(!nextCore.compareAndSet(null, newCore)) {
            return
        }
        for(i in 0 until array.size) {
            while (true) {
                val x = array[i].value
                if(array[i].compareAndSet(x, Moved())) {
                    nextCore.value!!.put(i, x!!)
                    break
                }
            }
        }
    }
}

sealed class Cell
class Moved(): Cell()
class Current(val x: Any): Cell()

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME