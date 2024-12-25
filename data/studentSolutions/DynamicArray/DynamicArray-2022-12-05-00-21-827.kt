//package mpp.dynamicarray

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
        while (!core.value.put(index, element)) {
            continue
        }
    }

    override fun pushBack(element: E) {
        while (!core.value.tryPush(element)) {
            val current = core.value
            val next = current.getNext()
            core.compareAndSet(current, next)
            core.value.size()
        }
    }

    override val size: Int get() = core.value.size()
}


private class Core<E>(
    capacity: Int,
) {
    private val array = atomicArrayOfNulls<Any>(capacity)
    private val _size = atomic(0)
    private val next: AtomicRef<Core<E>?> = atomic(null)

    fun size(): Int = min(_size.value, array.size)

    fun get(index: Int): E {
        require(index < size())

        val curValue = array[index].value
        if (curValue is Closed<*>) {
            val closedValue = curValue.closedValue
            /*val nextValue = next.value!!.array[index].value

            if (nextValue != null) {
                @Suppress("UNCHECKED_CAST")
                return nextValue as E
            }*/

            @Suppress("UNCHECKED_CAST")
            return closedValue as E
        }

        @Suppress("UNCHECKED_CAST")
        return curValue as E
    }

    fun tryPush(value: E): Boolean {
        if (next.value != null) return false

        val index = _size.value
        if (index >= array.size) return false

        if (array[index].value != null) {
            _size.compareAndSet(index, index + 1)

            return false
        }

        val pushResult = array[index].compareAndSet(null, value)
        if (pushResult) {
            _size.compareAndSet(index, index + 1)
        }

        return pushResult
    }

    fun put(index: Int, value: E) : Boolean {
        require(index < size())

        val curArrayValue = array[index].value
        if (curArrayValue is Closed<*>) {
            val nextValue = next.value!!.array[index].value

            if (nextValue is Closed<*>) {
                return false
            }

            return next.value!!.array[index].compareAndSet(nextValue, value)
        }

        return array[index].compareAndSet(curArrayValue, value)
    }

    fun getNext(): Core<E> {
        if (size() < array.size) return this

        next.compareAndSet(null, Core(array.size * 2 + 1))

        for (i in 0 until array.size) {
            while (true) {
                val currentValue = array[i].value
                if (currentValue is Closed<*>) {
                    next.value!!.array[i].compareAndSet(null, currentValue.closedValue)
                    break
                }
                else {
                    if (array[i].compareAndSet(currentValue, Closed(currentValue))) {
                        next.value!!.array[i].compareAndSet(null, currentValue)
                        break
                    }
                }
            }
        }

        next.value!!._size.compareAndSet(0, array.size)

        return next.value!!
    }


    private class Closed<E>(val closedValue: E)
}


private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME