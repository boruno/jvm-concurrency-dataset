//package mpp.dynamicarray

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
    private val nextCoreRef: AtomicRef<Core<E>?> = atomic(null)
    private var curCapacity = INITIAL_CAPACITY;

    @Suppress("UNCHECKED_CAST")
    override fun get(index: Int): E {
        val nextCore = nextCoreRef.value
        if (nextCore != null) {
            val nextCoreVal = nextCore.get(index)
            if (nextCoreVal != null) {
                return nextCoreVal
            }
        }
        return core.value.get(index) as E
    }

    override fun put(index: Int, element: E) {
        val nextCore = nextCoreRef.value
        if (nextCore != null) {
            nextCore.set(index, element)
        } else {
            core.value.set(index, element)
        }
    }

    override fun pushBack(element: E) {
        while (true) {
            if (core.value.pushBack(element) || (nextCoreRef.value != null && nextCoreRef.value!!.pushBack(element))) {
                return
            } else if (nextCoreRef.compareAndSet(null, Core<E>(curCapacity * 2, curCapacity))) {
                nextCoreRef.value!!.copyElements(core.value)
                core.value = nextCoreRef.value!!
                nextCoreRef.value = null
                curCapacity *= 2
            }
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

    fun size(): Int {
        return _size.value
    }

    fun get(index: Int): E? {
        if (index < _size.value) {
            return array[index].value
        } else {
            throw IllegalArgumentException("get")
        }
    }

    fun set(index: Int, value: E) {
        if (index < _size.value) {
            array[index].value = value
        } else {
            throw IllegalArgumentException("set")
        }
    }

    fun pushBack(element: E): Boolean {
//        while (true) {
        for (aaa in 1..1000) {
            val curSize = _size.value
            if (curSize < array.size) {
                if (array[curSize].compareAndSet(null, element)) {
                    _size.compareAndSet(curSize, curSize + 1)
                    return true
                } else {
                    _size.compareAndSet(curSize, curSize + 1)
                }
            } else {
                return false
            }
        }
        throw Exception("PUSHBACK EXCEPTION")
    }

    fun copyElements(oldCore: Core<E>) {
        for (i in 0 until oldCore.size()) {
            array[i].compareAndSet(null, oldCore.get(i))
        }
    }
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME