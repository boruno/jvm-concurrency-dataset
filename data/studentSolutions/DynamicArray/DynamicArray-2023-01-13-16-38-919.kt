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

class Moved<E>(val value: E)

@Suppress("UNCHECKED_CAST")
class DynamicArrayImpl<E> : DynamicArray<E> {
    private val core = atomic(Core<E>(INITIAL_CAPACITY))

    override fun get(index: Int): E {
        require(index < size)

        while (true) {
            if (index >= core.value.array.size) continue
            val value = core.value.array[index].value

            if (value is Moved<*>) {
                return value.value as E
            }
            else {
                return value as E
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
        val containerSize = core.value._size.getAndIncrement()
        var currentArray = core.value
        val newArray = Core<E>(containerSize * 2)
        newArray._size.value = containerSize

        while (true) {
//            var currentArray = core.value

//            if (containerSize >= currentArray.array.size) {
//                val newArray = Core<E>(containerSize * 2)
//                newArray._size.value = containerSize + 1

                if (currentArray.next.compareAndSet(null, newArray)) {
                    for (i in 0 until containerSize) {
                        while (true) {
                            val currentValue = currentArray.array[i].value

                            if (currentArray.array[i].compareAndSet(currentValue, Moved(currentValue))) {
                                newArray.array[i].value = currentValue
                                break
                            }
//                            val currentValue = currentArray.array[i].getAndSet(Moved(currentArray.array[i]))
////                            val currentValue = currentArray.array[i].getAndSet(null)
//
//                            if (currentValue is Moved<*>) continue
//                            else {
//                                newArray.array[i].value = currentValue
//                                break
//                            }
                        }
                    }
                    if (newArray.array[containerSize].compareAndSet(null, element)) {
                        if (core.compareAndSet(currentArray, newArray)) {
                            return
                        }
                    }
//                    newArray.array[containerSize].value = element
//                    if (core.compareAndSet(currentArray, newArray)) {
//                        return
//                    }
                } else {
                    currentArray = currentArray.next.value!!
                }
//            } else {
//                if (currentArray.array[containerSize].compareAndSet(null, element)) {
//                    return
//                }
//            }
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

//    val size: Int = _size.value

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E {
        require(index < _size.value)
        return array[index].value as E
    }
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME