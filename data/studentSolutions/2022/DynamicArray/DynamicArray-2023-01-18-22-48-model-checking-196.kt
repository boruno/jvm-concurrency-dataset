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
    private val core = atomic(Core<E>(INITIAL_CAPACITY, null, 0))

    override fun get(index: Int): E {
        while (true) {
            val result = core.value.get(index, false)
            if (result == "") {
                continue
            }
            return result as E
        }
    }

    override fun put(index: Int, element: E) {
        while (true) {
            if (core.value.put(index, element) == "SUCCESS") return
        }
    }


    override fun pushBack(element: E) {
        while (true) {
            val curCore = core.value
            if (curCore.pushBack(element) == "SUCCESS") return
            val newCore = Core(curCore.size * 2, curCore, curCore.size)
            newCore.pushBack(element)
            if (core.compareAndSet(curCore, newCore)) return
        }
    }

    override val size: Int get() = core.value.size
}

private class Core<E>(
    capacity: Int, _previous: Core<E>?, __size: Int
) {
    private val array = atomicArrayOfNulls<Any?>(capacity)
    private val _size = atomic(__size)
    val previousCore: Core<E>? = _previous

    val size: Int = _size.value

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int, flag: Boolean): Any {
        require(index < size)
        var curValue = array[index].value
        if (curValue != null) {
            if (flag) curValue = array[index].getAndSet("")
            return curValue!!
        }
        var newValue = previousCore!!.get(index, true)
        if (array[index].compareAndSet(null, newValue)) {
            if (flag) newValue = array[index].getAndSet("")!!
            return newValue
        } else {
            return array[index].value!!
        }
    }

    fun put(index: Int, element: E): String {
        val currentValue = get(index, false)
        if (currentValue == "") return "FAIL"
        if (array[index].compareAndSet(currentValue, element)) {
            return "SUCCESS"
        }
        return "FAIL"
    }

    fun pushBack(element: E): String {
        while (true) {
            val currentSize = size
            if (currentSize >= array.size) {
                return "FAIL"
            }

            if (array[currentSize].compareAndSet(null, element)) {
                _size.getAndIncrement()
                return "SUCCESS"
            }
        }
    }
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME