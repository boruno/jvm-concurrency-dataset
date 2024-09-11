package mpp.dynamicarray

import kotlinx.atomicfu.*
import kotlin.math.min

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
        while (!core.value.tryPut(index, element)) {
            continue
        }
    }

    override fun pushBack(element: E) {
        while (!core.value.tryPush(element)) {
            val (success, next) = core.value.getNext()

            if (success) {
                core.getAndSet(next!!)
            }
        }
    }

    override val size: Int get() = core.value.size
}

private class Core<E>(
    capacity: Int,
) {
    private val array = atomicArrayOfNulls<E>(capacity)
    private val _size = atomic(0)
    private val next: AtomicRef<Core<E>?> = atomic(null)

    val size: Int = min(_size.value, array.size)

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E {
        require(index < size)
        return array[index].value as E
    }

    fun tryPush(value: E): Boolean {
        val index = _size.getAndIncrement()

        if (index >= array.size) return false

        return array[index].compareAndSet(null, value)
    }

    fun tryPut(index: Int, value: E): Boolean {
        if (index >= array.size) throw IllegalArgumentException("Index out of range: $index")
        if (next.value != null && next.value!!.size > index) {
            return false
        }

        array[index].getAndSet(value)
        return true
    }

    fun getNext() : Pair<Boolean, Core<E>?> {
        if (!next.compareAndSet(null, Core(array.size * 2))) {
            return Pair(false, null)
        }

        for (i in 0 until array.size) {
            next.value!!._size.getAndIncrement()
            next.value!!.array[i].getAndSet(array[i].value)
        }

        return Pair(true, next.value)
    }
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME