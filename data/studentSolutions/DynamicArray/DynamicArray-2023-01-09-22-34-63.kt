//package mpp.dynamicarray
//
import kotlinx.atomicfu.*
//
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

//sealed interface Value {
//    data class NonFixedValue<E>(val value: E) : Value
//    data class FixedValue<E>(val value: E) : Value
//    object Moved : Value
//}

@Suppress("UNCHECKED_CAST")
class DynamicArrayImpl<E> : DynamicArray<E> {
    private val core = atomic(Core<E>(INITIAL_CAPACITY))
    private val containerSizeCounter = atomic(0)

    override fun get(index: Int): E {
        require(index < size)

        while (true) {
            if (index >= core.value.array.size) continue
            val value = core.value.array[index].value

            if (value != null) {
                return value
            }
        }
    }

    override fun put(index: Int, element: E) {
        require(index < size)

        while (true) {
            if (index >= core.value.array.size) continue

            val value = core.value.array[index].value

            if (value != null) {
                val currentValue = core.value.array[index].getAndSet(null)
                if (currentValue != null) {
                    core.value.array[index].compareAndSet(null, element)
                    return
                }
            }
        }
    }

    override fun pushBack(element: E) {
        val containerSize = containerSizeCounter.getAndIncrement()

        while (true) {
            val currentArray = core.value

            if (containerSize >= currentArray.array.size) {
                val newArray = Core<E>(currentArray.size * 2)

                if (core.value.next.compareAndSet(null, newArray)) {
                    for (i in 0 until currentArray.size) {

                        while (true) {
                            val currentValue = currentArray.array[i].getAndSet(null)

                            if (currentValue != null) {
                                newArray.array[i].value = currentValue
                                return
                            }
                        }

                    }
                    newArray.array[currentArray.size].value = element
                    if (core.compareAndSet(currentArray, newArray)) {
                        return
                    }
                }
            } else {
                if (currentArray.array[containerSize].compareAndSet(null, element)) {
                    return
                }
            }
        }
    }

    override val size: Int
        get() {
            return containerSizeCounter.value
        }
}

private class Core<E>(
    capacity: Int,
) {
    val array = atomicArrayOfNulls<E>(capacity)
    val _size = atomic(capacity)
    val next: AtomicRef<Core<E>?> = atomic(null)

    val size: Int = _size.value

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E {
        require(index < size)
        return array[index].value as E
    }
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME