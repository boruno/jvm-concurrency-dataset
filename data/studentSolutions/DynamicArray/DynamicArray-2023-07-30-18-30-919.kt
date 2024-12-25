//package day4

import kotlinx.atomicfu.*

// This implementation never stores `null` values.
class DynamicArray<E : Any> {
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
            if (nextCore == null) {
                val currentSize = currentCore.size.value
                if (currentSize == currentCore.capacity) {
                    val newCore = Core(currentCore.capacity * 2)
                    newCore.size.value = currentSize
                    currentCore.next.compareAndSet(null, newCore)
                    continue
                }
                if (!currentCore.array[currentSize].compareAndSet(null, element)) {
                    currentCore.size.compareAndSet(currentSize, currentSize + 1)
                    continue
                }
                while (true) {
                    currentCore.size.compareAndSet(currentSize, currentSize + 1)
                    if (currentCore.size.value > currentSize) return
                }
            } else {
                var index = 0
                while (index < currentCore.size.value) {
                    val curElement = currentCore.array[index].value
                    if (curElement is Frozen) {
                        nextCore.array[index].compareAndSet(null, curElement.element)
                        index++
                    } else {
                        currentCore.array[index].compareAndSet(curElement, Frozen(curElement!!))
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
            val currentCore = core.value
            val currentSize = currentCore.size.value
            if (index < currentSize) {
                return
            }

            val curCellValue = currentCore.array[index].value
            if (curCellValue is Frozen) {
                val nextCore = currentCore.next.value!!
                var copyIndex = 0
                while (copyIndex < currentCore.size.value) {
                    val curElement = currentCore.array[copyIndex].value
                    if (curElement is Frozen) {
                        nextCore.array[copyIndex].compareAndSet(null, curElement.element)
                        copyIndex++
                    } else {
                        if (currentCore.array[copyIndex].compareAndSet(curElement, Frozen(curElement!!))) {
                            nextCore.array[copyIndex].compareAndSet(null, curElement)
                            copyIndex++
                        }
                    }
                }
                core.compareAndSet(currentCore, nextCore)
            } else {
                if (currentCore.array[index].compareAndSet(curCellValue, element)) {
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
        val currentCore = core.value
        val currentSize = currentCore.size.value
        require(index < currentSize) { "index must be lower than the array size" }
        // TODO: check that the cell is not "frozen",
        // TODO: unwrap the element in this case.

        val curCellValue = currentCore.array[index].value
        if (curCellValue is Frozen) {
            return curCellValue.element as E
        }
        return curCellValue as E
    }

    private class Frozen(val element: Any)

    private class Core(
        val capacity: Int
    ) {
        val array = atomicArrayOfNulls<Any?>(capacity)
        val size = atomic(0)
        val next = atomic<Core?>(null)
    }
}