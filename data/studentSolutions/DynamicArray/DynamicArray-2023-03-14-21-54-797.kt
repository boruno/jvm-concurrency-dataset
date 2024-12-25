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
    private val sizeRef = atomic(0)

    override fun get(index: Int): E {
        require(index < sizeRef.value)
        return getHelper(index)
    }

    private fun getHelper(index: Int): E {
        while (true) {
            val cur = core.value
            val wrapped = cur.get(index) ?: continue

            if (wrapped is Normal || wrapped is Fixed) {
                return wrapped.value!!
            }

            copyArray()
        }
    }

    override fun put(index: Int, element: E) {
        require(index < sizeRef.value)
        putHelper(index, element)
    }

    private fun putHelper(index: Int, element: E) {
        while (true) {
            val cur = core.value
            val wrapper = cur.get(index)

            if (wrapper === null || wrapper is Normal) {
                if (cur.casValue(index, wrapper, Normal(element))) return
            }

            copyArray()
        }
    }

    override fun pushBack(element: E) {
        while (true) {
            val index = sizeRef.value
            increaseCapacityIfNeeded(index + 1)

            val wrapped = core.value.get(index)

            if (wrapped == null) {
                if (core.value.casValue(index, null, Normal(element))) {
                    incSize(index)
                    return
                }
            } else if (wrapped is Normal || wrapped is Fixed) {
                incSize(index)
            }

            copyArray()
        }
    }

    override val size: Int get() = sizeRef.value

    private fun increaseCapacityIfNeeded(index: Int) {
        while (core.value.capacity < index) {
            val cur = core.value

            if (cur.next.value !== null) {
                copyArray()
            } else {
                val newSize = 2 * index
                if (cur.next.compareAndSet(null, Core(newSize))) {
                    copyArray()
                    break
                }
            }
        }
    }

    private fun incSize(expected: Int): Boolean {
        return sizeRef.compareAndSet(expected, expected + 1)
    }

    private fun copyArray() {
        val cur = core.value
        val next = cur.next.value ?: return

        for (i in 0 until cur.capacity) {
            while (true) {
                val oldRef = cur.array[i]
                val oldValue = oldRef.value

                var success: Boolean

                if (oldValue == null) {
                    success = oldRef.compareAndSet(null, Moved())
                } else if (oldValue is Normal) {
                    val res = oldRef.compareAndSet(oldValue, Fixed(oldValue.value))
                    if (res) {
                        next.array[i].compareAndSet(null, Normal(oldValue.value))
                    }
                    success = res
                } else if (oldValue is Fixed) {
                    next.array[i].compareAndSet(null, Normal(oldValue.value))
                    success = true
                } else {
                    success = true
                }

                if (success) {
                    break
                }
            }
        }
        core.compareAndSet(cur, next)
    }
}

private class Core<E>(val capacity: Int) {
    val array = atomicArrayOfNulls<Wrapped<E>>(capacity)
    val next = atomic<Core<E>?>(null)

    fun get(index: Int): Wrapped<E>? =
        array[index].value

    fun casValue(index: Int, expected: Wrapped<E>?, update: Wrapped<E>?): Boolean {
        return array[index].compareAndSet(expected, update)
    }
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME

sealed interface Wrapped<E> {
    val value: E?
}

class Fixed<E>(override val value: E) : Wrapped<E>
class Normal<E>(override val value: E) : Wrapped<E>
class Moved<E>(override val value: E? = null) : Wrapped<E>