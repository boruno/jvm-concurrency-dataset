//package mpp.dynamicarray

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
    private val core: AtomicRef<Core<E>?> = atomic(Core<E>(INITIAL_CAPACITY, 0))

    override fun get(index: Int): E = core.value!!.get(index)!!

    override fun put(index: Int, element: E) {
        core.value!!.put(index, Current(element))
    }

    override fun pushBack(element: E) {
        while (true) {
            val currCore = core.value
            if (currCore!!.pushBack(element)) {
                return
            }
            currCore.resize()
            core.compareAndSet(currCore, core.value!!.nextCore.value)
        }
    }

    override val size: Int get() = core.value!!.size
}

private class Core<E : Any>(
    capacity: Int,
    initSize: Int,
) {
    val array = atomicArrayOfNulls<Cell>(capacity)
    val _size = atomic(initSize)
    val nextCore: AtomicRef<Core<E>?> = atomic(null)
    val size: Int get() { return _size.value }

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E? {
        require(index < size)
        return when(val cell = array[index].value) {
            is Current -> cell.x as E?
            is Moved -> {
                val x = this.nextCore.value!!.get(index)
                if (this.nextCore.value!!.get(index) == null) {return cell.x as E? } else {return x}
            }
            null -> return null
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
            if(currSize == array.size) {
                return false
            }
            if (array[currSize].value != null) {
                _size.compareAndSet(currSize, currSize + 1)
                continue
            }
            if(array[currSize].compareAndSet(null, Current(element))) {
                _size.compareAndSet(currSize, currSize + 1)
                return true
            }
        }
    }
    fun resize() {
        val newCore = Core<E>(array.size * 2, array.size)
        if(!nextCore.compareAndSet(null, newCore)) {
            return
        }
        for(i in 0 until array.size) {
            while (true) {
                val x = array[i].value ?: break
                if(array[i].compareAndSet(x, Moved(x))) {
                    nextCore.value!!.array[i].compareAndSet(null, x)
                    break
                }
            }
        }
    }
}

sealed class Cell
class Moved(val x: Any): Cell()
class Current(val x: Any): Cell()

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME