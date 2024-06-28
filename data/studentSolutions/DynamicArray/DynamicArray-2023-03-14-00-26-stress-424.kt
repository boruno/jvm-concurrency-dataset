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
    private val _size = atomic(0)

    override fun get(index: Int): E {
        require(index <_size.value)  {
            throw IndexOutOfBoundsException("Index $index is out of bounds")
        }
//        return getHelper(index)
        while (true) {
            val cur = core.value
            val wrapped = cur.get(index) ?: continue
            if (wrapped is Normal || wrapped is Fixed) {
                return wrapped.value!!
            }
            extendArray()
        }
    }

//    private fun getHelper(index: Int): E {
//    }

    override fun put(index: Int, element: E) {
        require(index <_size.value)  {
            throw IndexOutOfBoundsException("Index $index is out of bounds")
        }
//        putHelper(index, element)
        while (true) {
            val cur = core.value
            val wrapped = cur.get(index)
            if (wrapped === null || wrapped is Normal) {
                if (cur.casValue(index, wrapped, Normal(element))) return
            }
            extendArray()
        }
    }

    private fun putHelper(index: Int, element: E) {
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
        val curr = core.value
        val next = curr.next.value
        if (next === null) {
            return
        }
        (0 until curr.capacity).forEach { i ->
            while (true) {
                val oldRef = curr.array[i]
                val successReplace = when (val oldVal = oldRef.value) {
                    null -> oldRef.compareAndSet(null, Moved())

                    is Normal -> {
                        val res = oldRef.compareAndSet(oldVal, Fixed(oldVal.value))
                        if (res) {
                            next.array[i].compareAndSet(null, Normal(oldVal.value))
                        }
                        res
                    }

                    is Moved -> {
                        true
                    }

                    is Fixed -> {
                        next.array[i].compareAndSet(null, Normal(oldVal.value))
                        true
                    }
                }
                if (successReplace) {
                    break
                }
            }
        }
        core.compareAndSet(curr, next)
    }

    private fun checkIndex(index: Int) {
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