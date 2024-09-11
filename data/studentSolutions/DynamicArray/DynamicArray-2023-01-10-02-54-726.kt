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
    fun pushBack(element: E) {}

    /**
     * Returns the current size of this array,
     * it increases with [pushBack] invocations.
     */
    val size: Int
}

class DynamicArrayImpl<E> : DynamicArray<E> {
    private val core = atomic(Core<E>(INITIAL_CAPACITY))
    private var curCap = INITIAL_CAPACITY

    override fun get(index: Int): E = core.value.get(index)

    override fun put(index: Int, element: E) {
        core.value.set(index, element)
    }

    override fun pushBack(element: E) {
        core.value.pushBack(element)
        while (core.value.nextIsReady.value) {
            println("DELETE CAP = $curCap")
            curCap *= 2
            core.value = core.value.nextCoreRef.value!!
        }
    }

    override val size: Int get() = core.value.size()
}

private class Core<E>(
    capacity: Int,
    initSize: Int = 0
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

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E {
        if (nextCoreRef.value != null && nextCoreRef.value!!.array[index].value != null) {
            return nextCoreRef.value!!.get(index)
        }
        require(index < _size.value)
        return array[index].value as E
    }

    fun set(index: Int, value: E) {
        if (nextCoreRef.value != null) {
            nextCoreRef.value!!.set(index, value)
            return
        }
        require(index < _size.value)
        array[index].value = value
    }

    fun pushBack(element: E) {
        while (true) {
            if (nextCoreRef.value != null) {
                return nextCoreRef.value!!.pushBack(element)
            }
            try {
                if (array[_size.value].compareAndSet(null, element)) {
                    _size.incrementAndGet()
                    return
                }
            } catch (_: IndexOutOfBoundsException) {
                if (nextCoreRef.compareAndSet(null, Core(array.size * 2, array.size))) {
                    println("CREATED CAP = " + array.size * 2)
                    for (i in 0 until array.size) {
                        nextCoreRef.value!!.array[i].compareAndSet(null, array[i].value)
                    }
                    nextIsReady.value = true
                }
            }
        }
    }
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME