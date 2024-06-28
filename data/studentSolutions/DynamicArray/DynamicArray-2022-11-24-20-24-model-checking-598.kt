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

    override fun get(index: Int): E = core.value.get(index)

//    override fun put(index: Int, element: E) {
//        core.value.set(index, element)
//    }

    override fun pushBack(element: E) {
        while (true) {
            val currentCore = core.value
            if (push(element, currentCore)) {
                return
            } else {
                val newCore = Core<E>(currentCore.capacity * 2)
                newCore._size.addAndGet(currentCore.capacity + 1)
                if (currentCore.next.compareAndSet(null, newCore)) {
                    for (i in 0 until currentCore.capacity) {
                        newCore.set(i, currentCore.get(i))
                    }
                    newCore.set(currentCore.capacity, element)
                    core.compareAndSet(currentCore, newCore)
                    return
                } else {
                    core.compareAndSet(currentCore, currentCore.next.value!!)
                }
            }
        }
    }

    private fun push(elem: E, core: Core<E>): Boolean {
        val where = core._size.getAndIncrement()
        if (where < core.capacity) {
            if (core.array[where].compareAndSet(null, elem)) {
                return true
            } else {
                throw Exception1()
            }
        } else {
            val next = core.next.value
            if (next == null) {
                return false
            } else {
                return push(elem, next)
            }
        }
    }

    override val size: Int
        get() {
            val size = core.value._size.value
            return size
        }
}

private class Core<E>(
    val capacity: Int,
) {
    val array = atomicArrayOfNulls<E>(capacity)
    val _size = atomic(0)
    val next = atomic<Core<E>?>(null)

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E {
        require(index < _size.value)
        return array[index].value as E
    }

    fun set(index: Int, elem: E) {
        require(index < _size.value)
        array[index].value = elem
    }
}

class Exception1 : Exception()
class Exception2 : Exception()

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME