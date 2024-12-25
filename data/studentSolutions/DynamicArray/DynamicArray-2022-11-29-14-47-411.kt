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
//    fun put(index: Int, element: E)

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
//    private var nextCore: AtomicRef<Core<E>>? = null

    override fun get(index: Int): E = core.value.get(index)

//    override fun put(index: Int, element: E) {
//        TODO("Not yet implemented")
//    }

    override fun pushBack(element: E) {
        while (true) {
            val currentCore = core.value
            val where = currentCore._size.getAndIncrement()
            if (where < currentCore.capacity) {
                if (currentCore.array[where].compareAndSet(null, element)) {
                    return
                }
            } else {
                var nextCore = Core<E>(currentCore.capacity * 2, currentCore.capacity + 1)
                if (!currentCore.nextCore.compareAndSet(null, nextCore)) {
                    nextCore = currentCore.nextCore.value!!
                }
                for (i in 0 until currentCore.capacity) {
                    nextCore.array[i].compareAndSet(null, currentCore.get(i))
                }
                if (nextCore.array[currentCore.capacity].compareAndSet(null, element)) {
                    core.compareAndSet(currentCore, nextCore)
                    return
                }
                core.compareAndSet(currentCore, nextCore)
            }
        }
    }

    override val size: Int get() = core.value._size.value
}

private class Core<E>(
    val capacity: Int,
) {

    constructor(capacity: Int, size: Int) : this(capacity) {
        this._size.value = size
    }

    val array = atomicArrayOfNulls<E>(capacity)
    val _size = atomic(0)

    val nextCore: AtomicRef<Core<E>?> = atomic(null)

//    val size: Int = _size.value

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E {
        require(index < _size.value)
        return array[index].value as E
    }
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME