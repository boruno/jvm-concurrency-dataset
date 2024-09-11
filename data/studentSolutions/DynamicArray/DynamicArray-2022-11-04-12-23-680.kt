package mpp.dynamicarray

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
        while (true) {
            coreToNext()
            val (x, b) = core.value.get(index)
            if (!b) {
                return x
            }
        }
    }

    override fun put(index: Int, element: E) {
        while (true) {
            coreToNext()
            val curCore = core.value
            val (x, b) = curCore.get(index)
            if (b) {
                continue
            }
            curCore.cas(index, x to false, element to false)
        }
    }

    override fun pushBack(element: E) {
        while (true) {
            val currentCore = core.value
            val currentSize = currentCore.size
            if (currentSize < currentCore.capacity) {
                if (currentCore.cas(currentSize + 1, null, element to false)) {
                    currentCore.increaseSize()
                    break
                }
            } else {
                if (currentCore.nextCore.value == null) {
                    currentCore.nextCore.compareAndSet(null, Core(currentCore.capacity * 2))
                }
                coreToNext()
            }
        }
    }

    private fun coreToNext() {
        val curCore = core.value
        val curNextCore = core.value.nextCore.value ?: return
        for (i in 0 until curCore.size) {
            curCore.setMoved(i)
           if (curNextCore.cas(i, null, curCore.get(i).first to false)) {
               curNextCore.increaseSize()
           }
        }
        core.compareAndSet(curCore, curNextCore)
    }

    override val size: Int get() = core.value.size
}

private class Core<E>(
    capacity: Int,
) {
    private val array = atomicArrayOfNulls<Pair<E, Boolean>>(capacity)
    private val _size = atomic(0)
    val nextCore: AtomicRef<Core<E>?> = atomic(null)

    val size: Int = _size.value
    val capacity = array.size

    fun get(index: Int): Pair<E, Boolean> {
        require(index < size)
        return array[index].value!!.first to array[index].value!!.second
    }

    fun cas(index: Int, expect: Pair<E,Boolean>?, value: Pair<E, Boolean>): Boolean {
        return array[index].compareAndSet(expect, value)
    }

    fun setMoved(index: Int) {
        while (true) {
            val cur = array[index].value!!
            if (array[index].compareAndSet(cur, cur.first to true)) {
                break
            }
        }
    }

    fun increaseSize() {
        _size.incrementAndGet()
    }
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME