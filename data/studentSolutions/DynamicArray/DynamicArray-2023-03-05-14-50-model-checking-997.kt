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
            if (curCore.pushBack(element)) return
            core.compareAndSet(curCore, curCore.resize())
        }
    }

    override val size: Int get() = core.value.size
}

private open class ArrayElement<E>(val element: E)

private class MovedElement<E>(element: E): ArrayElement<E>(element)

private class Core<E>(
    val capacity: Int,
    new_size: Int = 0
) {
    private val array = atomicArrayOfNulls<ArrayElement<E>>(capacity)
    private val _size = atomic(new_size)
    private val newCore: AtomicRef<Core<E>?> = atomic(null)

    val size get() = _size.value

    private fun casSizeInc(curSize: Int): Boolean {
        return _size.compareAndSet(curSize, curSize + 1)
    }

    fun get(index: Int): E? {
        require(index < size)
        return array[index].value!!.element

//        val value = array[index].value
//        if (value is MovedElement) {
//            return newCore.value!!.get(index) ?: return value.element
//        }
//
//        return value?.element
    }

    fun put(index: Int, element: E): Boolean {
        require(index < size)
        val prev = array[index].value
        if (prev is MovedElement) {
            return newCore.value!!.put(index, element)
        }
        return array[index].compareAndSet(prev, ArrayElement(element))
    }

    fun pushBack(element: E): Boolean {
        while (true) {
            val curSize = size
            if (curSize >= capacity) {
                return false
            }

            if (array[curSize].value != null) {
                casSizeInc(curSize)
                continue
            }

            if (array[curSize].compareAndSet(null, ArrayElement(element))) {
                casSizeInc(curSize)
                return true
            }
        }
    }

    fun resize() : Core<E> {
        newCore.compareAndSet(null, Core(capacity * CAPACITY_INCREASE_FACTOR, _size.value))

        for (i in 0 until capacity) {
            while (true) {
                val value = array[i].value ?: break
                if ((value is MovedElement) || (array[i].compareAndSet(value, MovedElement(value.element)))) {
                    newCore.value!!.array[i].compareAndSet(null, ArrayElement(value.element))
                    break
                }
            }
        }

        return newCore.value!!
    }
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME
private const val CAPACITY_INCREASE_FACTOR = 2