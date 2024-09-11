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
    private val core = atomic(Core<E>(INITIAL_CAPACITY))

    override fun get(index: Int): E = core.value.get(index)

    override fun put(index: Int, element: E) = core.value.set(index, element)

    override fun pushBack(element: E) {
        while (true) {
            val currentCore = core.value
            if (currentCore.size < currentCore.capacity) {
                currentCore.set(currentCore.size, element)
            } else {
                resize()
            }
        }
    }

    private fun resize() {
        while (true) {
            val newCore = Core<E>(core.value.size * 2)
            val currentCore = core.value
            currentCore.nextCore.compareAndSet(null, newCore)

            for (i in 0 until currentCore.size) {
                newCore.set(i, currentCore.get(i))
                currentCore.set(i, null)
            }
            if (core.compareAndSet(currentCore, newCore)) {
                return
            }
        }
    }

    override val size: Int get() = core.value.size
}

private class Core<E>(
    val capacity: Int,
) {
    private val array = atomicArrayOfNulls<E>(capacity)
    private val _size = atomic(0)
    val nextCore : AtomicRef<Core<E>?> = atomic(null)

    val size: Int = _size.value


    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E {
//        require(index < size)
        return array[index].value ?: nextCore.value?.get(index) as E
    }

    fun set(index: Int, element: E?) {
//        require(index < size)
        val next = nextCore.value
        if (next != null) {
            next.set(index, element)
            return
        }
        array[index].getAndSet(element)
    }
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME