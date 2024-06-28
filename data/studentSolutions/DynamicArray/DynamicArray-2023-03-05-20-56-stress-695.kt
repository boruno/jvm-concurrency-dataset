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

    override val size: Int get() = core.value.getSize()

    override fun get(index: Int): E {
        while (true) {
            return core.value.get(index) ?: continue
        }
    }

    override fun put(index: Int, element: E) {
        while (!core.value.put(index, element)) {
        }
        return
    }

    override fun pushBack(element: E) {
        while (true) {
            val curCore = core.value
            if (curCore.pushBack(element)) return
            val newCore = curCore.resize()
            core.compareAndSet(curCore, newCore)
        }
    }
}

private open class SimpleEl<E>(val element: E)

private class FlaggedEl<E>(val element: E)

private class Core<E>(
    val capacity: Int,
    new_size: Int = 0
) {
    private val array = atomicArrayOfNulls<Any?>(capacity)
    private val size = atomic(new_size)
    private val next: AtomicRef<Core<E>?> = atomic(null)

    fun get(index: Int): E? {
        require(index < size.value)
        var value = array[index].value ?: return null
        if (value is FlaggedEl<*>) {
            val nextCore = next.value
            if (nextCore != null) {
                return nextCore.get(index) ?: return value.element as E
            }
        }
        value = value as SimpleEl<E>
        return value?.element
    }

    fun getSize(): Int {
        return size.value
    }

    fun put(index: Int, element: E): Boolean {
        require(index < size.value)
        val prev = array[index].value
        if (prev is FlaggedEl<*>) {
            val nextCore = next.value
            if (nextCore != null) {
                return nextCore.put(index, element)
            }
        } else {
            return array[index].compareAndSet(prev, SimpleEl(element))
        }
        return false
    }

    fun pushBack(element: E): Boolean {
        while (true) {
            val size1 = size.value
            if (size1 >= capacity) {
                return false
            }

            if (array[size1].value != null) {
                size.compareAndSet(size1, size1 + 1)
                continue
            }

            if (array[size1].compareAndSet(null, SimpleEl(element))) {
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
                if (value is FlaggedEl<*>) {
                    processElInNextCore(i, value)
//                    val nextCore = next.value
//                    if (nextCore == null) {
//                        break
//                    }
//                    nextCore.array[i].compareAndSet(null, SimpleEl(value.element))
                    break
                }
                value = value as SimpleEl<E>
                if (array[i].compareAndSet(value, FlaggedEl(value.element))) {
                    processElInNextCore(i, value)
//                    val nextCore = next.value
//                    if (nextCore == null) {
//                        break
//                    }
//                    val newEl = SimpleEl(value.element)
//                    nextCore.array[i].compareAndSet(null, newEl)
                    break
                }
            }
        }
        return next.value!!
    }

    private fun processElInNextCore(index: Int, value: Any) {
        val newValue = value as SimpleEl<E>
        val nextCore = next.value
        if (nextCore == null) {
            return
        }
        val newEl = SimpleEl(value.element)
        nextCore.array[index].compareAndSet(null, newEl)
    }
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME
private const val CAPACITY_INCREASE_FACTOR = 2