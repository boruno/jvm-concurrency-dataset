//package mpp.dynamicarray

import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls

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
        moveCore()
        require(core.value.size > index)
        return core.value.get(index)
    }

    override fun put(index: Int, element: E) {
        moveCore()
        require(core.value.size > index)
        core.value.set(index, element)
    }

    override fun pushBack(element: E) {
        while (true) {
            moveCore()
            val curCore = core.value
            if (curCore.isFull) {
                curCore.nextCore.compareAndSet(null, Core(curCore.capacity * 2))
            } else {
                val sz = curCore.size
                if (curCore.cas(sz, null, element)) {
                    return
                }
            }
        }
    }

    override val size: Int
        get() {
            moveCore()
            return core.value.size
        }

    private fun moveCore() {
        while (true) {
            val curCore = core.value
            val nextCore = curCore.nextCore.value ?: return
            for (i in 0 until curCore.capacity) {
                nextCore.cas(i, null, curCore.get(i))
            }
            core.compareAndSet(curCore, nextCore)
        }
    }
}

private class Core<E>(
    val capacity: Int,
) {
    val size: Int
        get() {
            for (i in 0 until capacity) {
                if (array[i].value == null) {
                    return i
                }
            }
            return capacity
        }
    private val array = atomicArrayOfNulls<E>(capacity)
    val nextCore: AtomicRef<Core<E>?> = atomic(null)

    fun get(index: Int): E {
        return array[index].value!!
    }

    fun cas(index: Int, expect: E?, newValue: E): Boolean {
        return array[index].compareAndSet(expect, newValue)
    }

    fun set(index: Int, element: E) {
        array[index].value = element
    }

    val isFull: Boolean
        get() = array[capacity - 1].value != null
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME