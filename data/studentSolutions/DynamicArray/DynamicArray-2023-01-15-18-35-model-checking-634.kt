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
    private val curCap = atomic(INITIAL_CAPACITY)

    @Suppress("UNCHECKED_CAST")
    override fun get(index: Int): E = core.value.get(index) as E

    override fun put(index: Int, element: E) {
        core.value.set(index, element)

    }

    override fun pushBack(element: E) {
        core.value.pushBack(element);
//        while (true) {
//        for (aaa in 0..1000) {
//            val localCore = core.value
//            if (localCore.pushBack(element)) {
//                return
//            }
//            val nextCore = localCore.setNextCore()
//            if (nextCore != null) {
//                core.compareAndSet(localCore, nextCore)
//            }
//        }
//        throw Exception("PUSHBACK 1")
    }


    override val size: Int get() = core.value.size()
}

private class Core<E>(
    capacity: Int,
    initSize: Int
) {
    private val array = atomicArrayOfNulls<E>(capacity)
    private val _size = atomic(initSize)
    private val nextCoreRef: AtomicRef<Core<E>?> = atomic(null)

    fun size(): Int {
        if (nextCoreRef.value != null) {
            return nextCoreRef.value!!.size()
        }
        return _size.value
    }

    fun get(index: Int): E? {
        if (nextCoreRef.value != null) {
            val nextCoreGet = nextCoreRef.value!!.get(index)
            if (nextCoreGet != null) {
                return nextCoreGet
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
//            return
        }
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
            } else if (nextCoreRef.value != null) {
                return nextCoreRef.value!!.pushBack(element)
            } else {
                setNextCore()
            }
        }
        throw Exception("PUSHBACK EXCEPTION")
    }

    fun setNextCore(): Core<E>? {
        if (nextCoreRef.compareAndSet(null, Core(array.size * 2, array.size))) {
            for (i in 0 until array.size) {
                nextCoreRef.value!!.array[i].value = array[i].value
            }
            return nextCoreRef.value
        }
        return null
    }
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME