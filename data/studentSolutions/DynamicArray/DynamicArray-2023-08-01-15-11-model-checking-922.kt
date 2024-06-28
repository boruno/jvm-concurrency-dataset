package day4

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
            val curCore = core.value
            val nextCore = curCore.next.value
            if (nextCore == null) {
                val curSize = curCore.size.value
                if (curSize == curCore.capacity) {
                    val newCore = Core(curCore.capacity * 2)
                    newCore.size.value = curSize * 2 
                    curCore.next.compareAndSet(null, newCore)
                    continue
                }
                if (curCore.array[curSize].compareAndSet(null, element)) {
                    while (true) {
                        curCore.size.compareAndSet(curSize, curSize + 1)
                        if (curCore.size.value > curSize) {
                            return
                        }
                    }
                }
                curCore.size.compareAndSet(curSize, curSize + 1)
            } else {
                var tmp = 0
                while (tmp < curCore.size.value) {
                    val curElement = curCore.array[tmp].value
                    if (curElement is Frozen) {
                        nextCore.array[tmp].compareAndSet(null, curElement.element)
                        tmp++
                    } else {
                        curCore.array[tmp].compareAndSet(curElement, Frozen(curElement!!))
                    }
                }
                core.compareAndSet(curCore, nextCore)
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
            val curCellValue = curCore.array[index].value
            if (curCellValue is Frozen) {
                val nextCore = curCore.next.value
                requireNotNull(nextCore) { "next core value cant be null" }
                var tmpIndex = 0
                while (tmpIndex < curCore.size.value) {
                    val copyCellValue = curCore.array[tmpIndex].value
                    if (copyCellValue is Frozen) {
                        nextCore.array[tmpIndex].compareAndSet(null, copyCellValue.element)
                        tmpIndex++
                    } else {
                        requireNotNull(copyCellValue) { "cant be null" }
                        if (curCore.array[tmpIndex].compareAndSet(copyCellValue, Frozen(copyCellValue))) {
                            nextCore.array[tmpIndex].compareAndSet(null, copyCellValue)
                            tmpIndex++
                        }
                    }
                }
                core.compareAndSet(curCore, nextCore)
                continue
            } else {
                if (curCore.array[index].compareAndSet(curCellValue, element)) {
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
        val curCoreValue = curCore.array[index].value
        return if (curCoreValue is Frozen) {
            curCoreValue.element
        } else {
            curCoreValue
        } as E
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