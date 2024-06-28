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

    override fun get(index: Int): E {
        while (true) {
            return core.value.get(index) ?: continue
        }
    }

    override fun put(index: Int, element: E) {
        while (true) {
            if (!core.value.put(index, element)) continue
            return
        }
    }

    override fun pushBack(element: E) {
        while (true) {
            val curCore = core.value
            if (curCore.append(element)) return
            core.compareAndSet(curCore, curCore.resize())
        }
    }

    override val size: Int get() = core.value.getSize()
}

private open class ArrayElement<E>(val element: E)

private class MovedElement<E>(val element: E)

private class Core<E>(
    val capacity: Int,
    new_size: Int = 0
) {
    private val array = atomicArrayOfNulls<Any?>(capacity)
    private val size = atomic(new_size)
    private val next: AtomicRef<Core<E>?> = atomic(null)

    fun get(index: Int): E? {
        require(index < size.value)
        var value = array[index].value
        if (value is MovedElement<*>) {
            val nextCore = next.value
            if (nextCore != null) {
                return nextCore.get(index) ?: return value.element as E
            }
        }
        value = value as ArrayElement<E>
        return value?.element
    }

    fun getSize(): Int {
        return size.value
    }

    fun put(index: Int, element: E): Boolean {
        require(index < size.value)
        val prev = array[index].value
        if (prev is MovedElement<*>) {
            val nextCore = next.value
            if (nextCore != null) {
                return nextCore.put(index, element)
            }
        } else {
            return array[index].compareAndSet(prev, ArrayElement(element))
        }
        return false
    }

    fun append(element: E): Boolean {
        while (true) {
            val size1 = size.value
            if (size1 >= capacity) {
                return false
            }

            if (array[size1].value != null) {
                size.compareAndSet(size1, size1 + 1)
                continue
            }

            if (array[size1].compareAndSet(null, ArrayElement(element))) {
                size.compareAndSet(size1, size1 + 1)
                return true
            }
        }
    }

    fun resize() : Core<E> {
        next.compareAndSet(null, Core(capacity * CAPACITY_INCREASE_FACTOR, size.value))
        for (i in 0..capacity-1) {
            while (true) {
                var value = array[i].value ?: break
                if (value is MovedElement<*>) {
                    next.value!!.array[i].compareAndSet(null, ArrayElement(value.element))
                    break
                }
                value = value as ArrayElement<E>
                if (array[i].compareAndSet(value, MovedElement(value.element))) {
                    next.value!!.array[i].compareAndSet(null, ArrayElement(value.element))
                    break
                }
            }
        }
        return next.value!!
    }
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME
private const val CAPACITY_INCREASE_FACTOR = 2