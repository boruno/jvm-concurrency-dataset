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

    override fun get(index: Int): E = core.value.get(index)

    override fun put(index: Int, element: E) {
        require(index < size)
        while(true) {
            val nextCore = core.value.next.value
            val curCore = core.value
            if(nextCore != null) {
                core.compareAndSet(curCore, nextCore)
                continue
            }
            val old = get(index)
            core.value.cas(index, old, element)
        }
    }

    override fun pushBack(element: E) {
        while(true) {
            val curCore = core.value
            val curCoreCapacity = curCore.capacity
            val curSize = curCore.size()
            if(curSize < curCoreCapacity) {
                if(curCore.cas(curSize, null, element)) {
                    curCore.casSize(curSize, curSize + 1)
                    return // successfully inserted the element
                }
                curCore.casSize(curSize, curSize + 1)
                continue // some thread has already inserted the element inside this cell, try again
            }

            resize(curCore)
        }
    }

    private fun resize(oldCore: Core<E>) {
        val newCore = Core<E>(oldCore.capacity * 2)
        if(!oldCore.next.compareAndSet(null, newCore)) return
        val next = oldCore.next.value
        for(i in 0 until oldCore.capacity) {
            val value = get(i) ?: break
            next!!.cas(i, null, value)
        }
        next!!.casSize(0, oldCore.size())
        core.compareAndSet(oldCore, next)
    }

    override val size: Int get() = core.value.size()
}

private class Core<E>(
    val capacity: Int,
) {
    val next = atomic<Core<E>?>(null)
    private val array = atomicArrayOfNulls<E>(capacity)
    private val size = atomic(0)

    fun cas(index: Int, expect: E?, update: E?): Boolean {
        return array[index].compareAndSet(expect, update)
    }

    fun size(): Int {
        return size.value
    }

    fun casSize(ssize: Int, nsize: Int) {
        size.compareAndSet(ssize, nsize)
    }

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E {
        require(index < size.value)
        return array[index].value as E
    }
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME