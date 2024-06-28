package mpp.dynamicarray

import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls

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
        if (index >= size) {
            throw IllegalArgumentException()
        }

        while (true) {
            val curArray = core.value

            if (index > curArray.capacity - 1)
                continue

            val value = curArray.array[index].value
            if (value != null) {
                return value.element.value
            }
        }
    }

    override fun put(index: Int, element: E) {
        if (index >= size) {
            throw IllegalArgumentException()
        }

        while (true) {
            val curArray = core.value

            if (index > curArray.capacity - 1)
                continue

            val value = curArray.array[index].value

            if (value != null) {
                if (value.element.compareAndSet(value.element.value, element) && curArray.array[index].value == value) {
                    return
                }
            }
        }
    }

    override fun pushBack(element: E) {
        val curSize = core.value.atomicSize.getAndIncrement()

        while (true) {
            val curArray = core.value

            if (curSize > curArray.capacity - 1) {
                val nextArray = Core<E>( 2 * curArray.capacity, curArray.capacity-1)

                if (curArray.next.compareAndSet(null, nextArray)) {
                    for (i in 0 until curArray.capacity) {
                        while (true) {
                            val value = curArray.array[i].getAndSet(null)
                            if (value != null) {
                                nextArray.array[i].compareAndSet(null, value)
                                break
                            }
                        }
                    }
                    core.compareAndSet(curArray, nextArray)
                }
            } else if (curArray.array[curSize].compareAndSet(null, CoreElement(atomic(element)))) {
                break
            }
        }
    }

    override val size: Int
        get() {
            return core.value.atomicSize.value
        }
}

private class CoreElement<T>(val element: AtomicRef<T>)

private class Core<E>(
    val capacity: Int,
    size: Int = 0
) {
    val array = atomicArrayOfNulls<CoreElement<E>>(capacity)
    val atomicSize = atomic(size)
    val next: AtomicRef<Core<E>?> = atomic(null)

//    @Suppress("UNCHECKED_CAST")
//    fun get(index: Int): E {
//        require(index < atomicSize.value)
//        return array[index].value as E
//    }
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME
