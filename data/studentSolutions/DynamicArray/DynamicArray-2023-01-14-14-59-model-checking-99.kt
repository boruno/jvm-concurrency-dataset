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

class Busy<E>(val value: E)

class Moved

@Suppress("UNCHECKED_CAST")
class DynamicArrayImpl<E> : DynamicArray<E> {
    private val core = atomic(Core<E>(INITIAL_CAPACITY))

    override fun get(index: Int): E {
        require(index < size)
        var curCore = core.value

        while (true) {
            if (index >= core.value.array.size) continue
//            var curCore = core.value
            val value = curCore.array[index].value

            if (value is Moved) {
                val curCoreNext = curCore.next.value
                if (curCoreNext != null) {
                    curCore = curCoreNext
                }
            } else if (value is Busy<*>) {
                return value.value as E
            } else {
                return value as E
            }
        }
    }

    override fun put(index: Int, element: E) {
        require(index < size)

        while (true) {
            if (index >= core.value.array.size) continue

            val value = core.value.array[index].value

            if (value is Busy<*>) continue else {
                val currentValue = core.value.array[index].getAndSet(Busy(core.value.array[index]))
                if (currentValue is Busy<*>) continue else {
                    core.value.array[index].compareAndSet(Busy(currentValue), element)
                    return
                }
            }
        }
    }

    override fun pushBack(element: E) {
        var containerSize = core.value._size.value
//        .getAndIncrement()
//        var currentArray = core.value

        while (true) {
            var currentArray = core.value

            if (containerSize >= currentArray.array.size) {
                val newArray = Core<E>(containerSize * 2)
                newArray._size.value = containerSize + 1

                if (currentArray.next.compareAndSet(null, newArray)) {
                    for (i in 0 until currentArray.array.size) {
                        while (true) {
                            val currentValue = currentArray.array[i].value

                            if (currentArray.array[i].compareAndSet(currentValue, Busy(currentValue))) {
                                newArray.array[i].value = currentValue
                                currentArray.array[i].compareAndSet(Busy(currentValue), Moved())
                                break
                            }
                        }
                    }
                    if (newArray.array[containerSize].compareAndSet(null, element)) {
                        core.value._size += 1
                        containerSize += 1
                        return
                    }
                }
            } else {
                if (currentArray.array[containerSize].compareAndSet(null, element)) {
                    continue
                } else {
                    containerSize += 1
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

//    val size: Int = _size.value

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E {
        require(index < _size.value)
        return array[index].value as E
    }
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME