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
        var currentCore = core.value

        while (true) {

            if (index >= currentCore.array.size) continue

            val value = currentCore.array[index].value

            if (value is Moved) {
                val currentCoreNext = currentCore.next.value
                if (currentCoreNext != null) {
                    currentCore = currentCoreNext
                    continue
                }
            }
            if (value is Busy<*>) return value.value as E
            if (value != null) return value as E
        }
    }

    override fun put(index: Int, element: E) {
        require(index < size)
        var currentCore = core.value

        while (true) {
            if (index >= currentCore.array.size) continue

            val value = currentCore.array[index].value

            if (value is Moved) {
                val currentCoreNext = currentCore.next.value
                if (currentCoreNext != null) {
                    currentCore = currentCoreNext
                    continue
                }
            }
            if (value is Busy<*>) {
                currentCore.next.value?.let {
                    move(currentCore, it)
                }
            }

            if (currentCore.array[index].compareAndSet(value, element)) return

        }
    }

    override fun pushBack(element: E) {
        while (true) {
            val currentArray = core.value
            val elementsAmount = currentArray._elementsCount.value

            if (elementsAmount >= currentArray.array.size) {
                val newArray = Core<E>(elementsAmount * 2)
                newArray._elementsCount.value = elementsAmount

                if (currentArray.next.compareAndSet(null, newArray)) {
                    move(currentArray, newArray)

                    val busyElement = Busy(element)
                    if (newArray.array[elementsAmount].compareAndSet(null, busyElement)) {
                        newArray._elementsCount.compareAndSet(elementsAmount, elementsAmount + 1)
                        newArray.array[elementsAmount].compareAndSet(busyElement, element)
                        core.compareAndSet(currentArray, newArray)
                        return
                    }

                } else {
                    val nextArray = currentArray.next.value ?: continue

                    move(currentArray, nextArray)

                    core.compareAndSet(currentArray, nextArray)
                    continue
                }
            } else {
                if (currentArray.array[elementsAmount].compareAndSet(null, element)) {
                    currentArray._elementsCount.compareAndSet(elementsAmount, elementsAmount + 1)
                    return
                } else {
                    currentArray._elementsCount.compareAndSet(elementsAmount, elementsAmount + 1)
                }
            }
        }
    }

    fun move(from: Core<*>, to: Core<*>) {
        for (i in 0 until from.array.size) {
            while (true) {
                val currentValue: Any? = from.array[i].value
                if (currentValue is Moved) break
                var settableValue = currentValue
                if (currentValue is Busy<*>) {
                    settableValue = currentValue.value
                }

                val busyElement = Busy(settableValue)
                if (from.array[i].compareAndSet(currentValue, busyElement)) {
                    to.array[i].value = settableValue
                    from.array[i].compareAndSet(busyElement, Moved)
                    break
                }
            }
        }
    }

    override val size: Int
        get() {
            return core.value._elementsCount.value
        }
}

class Core<E>(
    capacity: Int,
) {
    val array = atomicArrayOfNulls<Any?>(capacity)
    val _elementsCount = atomic(0)
    val next: AtomicRef<Core<E>?> = atomic(null)

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E {
        require(index < _elementsCount.value)
        return array[index].value as E
    }
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME