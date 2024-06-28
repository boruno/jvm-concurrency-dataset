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

data class Busy<E>(val value: E)

object Moved

@Suppress("UNCHECKED_CAST")
class DynamicArrayImpl<E> : DynamicArray<E> {
    private val core = atomic(Core<E>(INITIAL_CAPACITY))

    override fun get(index: Int): E {
        require(index < size)

        while (true) {
            val currentCore = core.value

            if (index >= currentCore.array.size) continue

            val value = currentCore.array[index].value

            if (value is Moved) continue

            if (value is Busy<*>) return value.value as E
            if (value != null) return value as E

        }
    }

    override fun put(index: Int, element: E) {
        require(index < size)

        while (true) {
            val currentCore = core.value
            if (index >= currentCore.array.size) continue

            val value = currentCore.array[index].value

            if (value is Moved) continue
            if (value is Busy<*>) continue

            if (currentCore.array[index].compareAndSet(value, element)) return

        }
    }

    override fun pushBack(element: E) {
        while (true) {
            val currentArray = core.value
            val containerSize = currentArray._size.value

            if (containerSize >= currentArray.array.size) {
                val newArray = Core<E>(containerSize * 2)
                newArray._size.value = containerSize + 1

                if (currentArray.next.compareAndSet(null, newArray)) {
                    for (i in 0 until currentArray.array.size) {
                        while (true) {
                            var currentValue: Any? = currentArray.array[i].value
                            if (currentValue is Busy<*>) {
                                currentArray.array[i].compareAndSet(currentValue, currentValue.value)
                                currentArray._size.getAndIncrement()
                            }

                            val busyElement = Busy(currentValue)
                            if (currentArray.array[i].compareAndSet(currentValue, busyElement)) {
                                newArray.array[i].value = currentValue
                                currentArray.array[i].compareAndSet(busyElement, Moved)
                                break
                            }
                        }
                    }
                    if (newArray.array[containerSize].compareAndSet(null, element)) {
                        if (core.compareAndSet(currentArray, newArray)) {
                            return
                        }
                    }
                }
            } else {
                val busyElement = Busy(element)
                currentArray._size.compareAndSet(containerSize, containerSize + 1)
                if (currentArray.array[containerSize].compareAndSet(null, busyElement)) {
                    currentArray.array[containerSize].compareAndSet(busyElement, element)
                    return
                }
            }
        }
    }

    override val size: Int
        get() {
            return core.value._size.value
        }
}

private class Core<E>(
    capacity: Int,
) {
    val array = atomicArrayOfNulls<Any?>(capacity)
    val _size = atomic(0)
    val next: AtomicRef<Core<E>?> = atomic(null)

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E {
        require(index < _size.value)
        return array[index].value as E
    }
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME