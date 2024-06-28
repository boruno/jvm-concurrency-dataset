package mpp.dynamicarray

import kotlinx.atomicfu.*
import java.lang.NullPointerException

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
    fun pushBack(element: E) {}

    /**
     * Returns the current size of this array,
     * it increases with [pushBack] invocations.
     */
    val size: Int
}

class DynamicArrayImpl<E> : DynamicArray<E> {
    private val core = atomic(Core<E>(INITIAL_CAPACITY, 0))

    @Suppress("UNCHECKED_CAST")
    override fun get(index: Int): E = core.value.get(index) as E

    override fun put(index: Int, element: E) {
        core.value.set(index, element)
    }

    override fun pushBack(element: E) {
        core.value.pushBack(element)
        val curCore = core.value
        if (curCore.nextIsReady.value) {
            core.compareAndSet(curCore, curCore.nextCoreRef.value!!)
        }
    }

    override val size: Int get() = core.value.size()
}

private class Core<E>(
    capacity: Int,
    initSize: Int
) {
    private val array = atomicArrayOfNulls<E>(capacity)
    private val _size = atomic(initSize)
    val nextIsReady = atomic(false)
    val nextCoreRef: AtomicRef<Core<E>?> = atomic(null)

    fun size(): Int {
        if (nextCoreRef.value != null) {
            return nextCoreRef.value!!.size()
        }
        return _size.value
    }

    fun get(index: Int): E? {
        if (nextCoreRef.value != null) {
            val nextGet = nextCoreRef.value!!.get(index)
            if (nextGet != null) {
                return nextGet
            }
        }
        if (index < _size.value) {
            return array[index].value
        } else {
            throw IllegalArgumentException("get")
        }
    }

    fun set(index: Int, value: E) {
        if (nextCoreRef.value != null) {
            nextCoreRef.value!!.set(index, value)
            return
        }
        if (index < _size.value) {
            array[index].value = value
        } else {
            throw IllegalArgumentException("set")
        }
    }

    fun pushBack(element: E) {
//        while (true) {
        for (aaa in 1..1000) {
            if (nextCoreRef.value != null) {
                return nextCoreRef.value!!.pushBack(element)
            }
            val curSize = _size.value
            if (curSize < array.size) {
                if (array[curSize].compareAndSet(null, element)) {
                    _size.compareAndSet(curSize, curSize + 1)
                    return
                } else {
                    _size.compareAndSet(curSize, curSize + 1)
                }
            } else if (nextCoreRef.compareAndSet(null, Core(_size.value * 2, _size.value))) {
                for (i in 0 until _size.value) {
                    while (array[i].value == null) {
                    }
                    nextCoreRef.value!!.array[i].compareAndSet(null, array[i].value)
                }
                nextIsReady.value = true
            }
        }
        throw Exception("PUSHBACK EXCEPTION")
    }
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME