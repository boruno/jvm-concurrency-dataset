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
    private val _size = atomic(0)

    override fun get(index: Int): E {
        checkIndex(index)
        return getHelper(index)
    }

    private fun getHelper(index: Int): E {
        while (true) {
            val cur = core.value
            val wrapped = cur.get(index) ?: continue
            if (wrapped is Normal || wrapped is Fixed) {
                return wrapped.value!!
            }
            extendArray()
        }
    }

    override fun put(index: Int, element: E) {
        checkIndex(index)
        putHelper(index, element)
    }

    private fun putHelper(index: Int, element: E) {
        while (true) {
            val cur = core.value
            val wrapped = cur.get(index)
            if (wrapped === null || wrapped is Normal) {
                if (cur.casValue(index, wrapped, Normal(element))) return
            }
            extendArray()
        }
    }

    override fun pushBack(element: E) {
        while (true) {
            val index = _size.value
            checkCapacity(index)

            val cur = core.value.get(index)

            if (cur == null) {
                if (core.value.casValue(index, null, Normal(element))) {
                    incSize(index)
                    return
                }
            } else if (cur is Normal || cur is Fixed) {
                incSize(index)
            }

            extendArray()
        }
    }

    override val size: Int get() = _size.value

    private fun extendArray() {
        val cur = core.value
        val next = cur.next.value ?: return

        for (i in 0 until cur.capacity) {
            while (true) {
                val old = cur.array[i]
                val oldValue = old.value

                var success = false

                if (oldValue == null) {
                    success = old.compareAndSet(null, Moved())
                } else if (oldValue is Normal) {
                    success = old.compareAndSet(oldValue, Fixed(oldValue.value))
                    if (success) {
                        next.array[i].compareAndSet(null, Normal(oldValue.value))
                    }
                } else if (oldValue is Fixed) {
                    next.array[i].compareAndSet(null, Normal(oldValue.value))
                    success = true
                } else {
                    success = true
                }

                if (success) break
            }
        }

        core.compareAndSet(cur, next)
    }

    private fun checkIndex(index: Int) {
        require(index in 0 until _size.value)  {
            throw IndexOutOfBoundsException("Index $index is out of bounds")
        }
    }

    private fun incSize(index: Int) {
        _size.compareAndSet(index, index + 1)
    }

    private fun checkCapacity(index: Int) {
        while (core.value.capacity < index) {
            val cur = core.value

            if (cur.next.value !== null) {
                extendArray()
            } else {
                val newSize = if (2 * cur.capacity < index) 2 * index else 2 * cur.capacity
                if (cur.next.compareAndSet(null, Core(newSize))) {
                    extendArray()
                    break
                }
            }
        }
    }
}

private class Core<E>(
    val capacity: Int,
) {
    val array = atomicArrayOfNulls<Wrapped<E>>(capacity)

    val next = atomic<Core<E>?>(null)

    fun get(index: Int): Wrapped<E>? {
        return array[index].value
    }

    fun casValue(index: Int, expected: Wrapped<E>?, update: Wrapped<E>?): Boolean {
        return array[index].compareAndSet(expected, update)
    }
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME

sealed interface Wrapped<E> {
    val value: E?
}
class Normal<E>(override val value: E) : Wrapped<E>
class Fixed<E>(override val value: E) : Wrapped<E>
class Moved<E>(override val value: E? = null) : Wrapped<E>