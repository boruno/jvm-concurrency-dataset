package day4

import kotlinx.atomicfu.*

// This implementation never stores `null` values.
class DynamicArray<E: Any> {
    private val core = atomic(Core(capacity = 1)) // Do not change the initial capacity

    /**
     * Adds the specified [element] to the end of this array.
     */
    fun addLast(element: E) {
        // TODO: Implement me!
        // TODO: Yeah, this is a hard task, I know ...
        while (true) {
            val currentCore = core.value
            val nextCore = currentCore.next.value
            val currentSize = currentCore.size.value
            if (nextCore == null) {
                if (currentSize == currentCore.capacity) {
                    currentCore.next.compareAndSet(
                        null,
                        Core(currentCore.capacity * 2).apply { size.value = currentSize })
                    continue
                }
                if (currentCore.array[currentSize].compareAndSet(null, element)) {
                    while (true) {
                        currentCore.size.compareAndSet(currentSize, currentSize + 1)
                        val updatedSize = currentCore.size.value
                        if (currentSize < updatedSize) {
                            return
                        }
                    }
                }
                currentCore.size.compareAndSet(currentSize, currentSize + 1)
            } else {
                var index = 0
                while (index < currentSize) {
                    val currentElement = currentCore.array[index].value
                    if (currentElement is Frozen) {
                        nextCore.array[index].compareAndSet(null, currentElement.element)
                        index++
                    } else {
                        currentCore.array[index].compareAndSet(currentElement, Frozen(currentElement!!))
                    }
                }
                core.compareAndSet(currentCore, nextCore)
            }
        }
    }

    /**
     * Puts the specified [element] into the cell [index],
     * or throws [IllegalArgumentException] if [index]
     * exceeds the size of this array.
     */
    fun set(index: Int, element: E) {
        while (true) {
            val curCore = core.value
            val curSize = curCore.size.value
            require(index < curSize) { "index must be lower than the array size" }
            // TODO: check that the cell is not "frozen"

            val indexValue = curCore.array[index].value

            if (indexValue is Frozen) {
                val nextCore = curCore.next.value ?: continue
                var copyIndex = 0
                while (copyIndex < curSize) {
                    val currentElement = curCore.array[copyIndex].value
                    if (currentElement is Frozen) {
                        nextCore.array[copyIndex].compareAndSet(null, currentElement.element)
                        copyIndex++
                    } else {
                        if (curCore.array[copyIndex].compareAndSet(currentElement, Frozen(currentElement!!))) {
                            nextCore.array[copyIndex].compareAndSet(null, currentElement)
                            copyIndex++
                        }
                    }
                }
                core.compareAndSet(curCore, nextCore)
            } else {
                if (curCore.array[index].compareAndSet(indexValue, element)) {
                    return
                }
            }
        }
    }

    /**
     * Returns the element located in the cell [index],
     * or throws [IllegalArgumentException] if [index]
     * exceeds the size of this array.
     */
    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E {
        val curCore = core.value
        val curSize = curCore.size.value
        require(index < curSize) { "index must be lower than the array size" }
        // TODO: check that the cell is not "frozen",
        // TODO: unwrap the element in this case.

        val cellValue = curCore.array[index].value
        return if (cellValue is Frozen) {
            cellValue.element as E
        } else {
            cellValue as E
        }
    }

    @JvmInline
    private value class Frozen(val element: Any)

    private class Core(
        val capacity: Int
    ) {
        val array = atomicArrayOfNulls<Any?>(capacity)
        val size = atomic(0)
        val next = atomic<Core?>(null)
    }
}