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
    private val sz = atomic(0)
    private val core = atomic(Core<E>(INITIAL_CAPACITY))

    override fun get(index: Int): E {
        if (index < 0 || index >= size) {
            throw IllegalArgumentException("Index out of bounds")
        }
        return core.value.cur[index].value!!.value
    }

    override fun put(index: Int, element: E) {
        if (index < 0 || index >= size) {
            throw IllegalArgumentException("Index out of bounds")
        }
        val toPut = Wrapper(element, false)
        while (true) {
            val old = core.value.cur[index].value
            if (!old!!.removed && core.value.cur[index].compareAndSet(old, toPut)) {
                return
            }
        }
    }

    override fun pushBack(element: E) {
        val toAdd = Wrapper(element, false)
        while (true) {
            val curSize = sz.value
            val oldCore = core.value
            val curCapacity = oldCore.capacity
            if (curSize + 1 > curCapacity) {
                val newCore = Core<E>(curCapacity * 2)
                var flag = false
                for (i in 0 until curCapacity) {
                    val cur = oldCore.cur[i].value ?: continue
                    newCore.cur[i].compareAndSet(null, Wrapper(cur.value, false))
                    if (!oldCore.cur[i].compareAndSet(cur, Wrapper(cur.value, true))) {
                        flag = true
                        break
                    }
                }
                if (flag || !core.compareAndSet(oldCore, newCore)) {
                    continue
                }
            }
            if (curSize < core.value.capacity && core.value.cur[curSize].compareAndSet(null, toAdd)) {
                sz.incrementAndGet()
                return
            }
        }
    }

    override val size: Int
        get() {
            return sz.value
        }
}

private class Wrapper<E>(val value: E, var removed: Boolean)

private class Core<E>(
    val capacity: Int
) {
    val cur = atomicArrayOfNulls<Wrapper<E>>(capacity)
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME