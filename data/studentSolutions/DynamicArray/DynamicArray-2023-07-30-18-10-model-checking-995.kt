package day4

import kotlinx.atomicfu.*

// This implementation never stores `null` values.
class DynamicArray<E: Any> {
    private val core = atomic(Core(capacity = 1)) // Do not change the initial capacity

    /**
     * Adds the specified [element] to the end of this array.
     */
    fun addLast(element: E) {
        while (true) {
            val curCore = core.value
            val nextCore = curCore.next.value
            val curSize = curCore.size.value

            if (nextCore == null && curSize < curCore.capacity && curCore.array[curSize].compareAndSet(null, element)) {
                if (increaseSize(curCore, curSize)) return
            } else if (nextCore != null) {
                copyElementsToNextCore(curCore, nextCore)
                core.compareAndSet(curCore, nextCore)
            } else if (curSize == curCore.capacity) {
                val newCore = Core(curCore.capacity * 2)
                newCore.size.value = curSize
                curCore.next.compareAndSet(null, newCore)
            }
        }
    }

    private fun increaseSize(curCore: Core, curSize: Int): Boolean {
        while (!curCore.size.compareAndSet(curSize, curSize + 1)) {
            if (curCore.size.value > curSize) return true
        }
        return false
    }

    private fun copyElementsToNextCore(curCore: Core, nextCore: Core) {
        for (index in 0 until curCore.size.value) {
            val curElement = curCore.array[index].value

            if (curElement is Frozen) {
                nextCore.array[index].compareAndSet(null, curElement.element)
            } else {
                curCore.array[index].compareAndSet(curElement, Frozen(curElement!!))
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

            when (val curCellValue = curCore.array[index].value) {
                is Frozen -> {
                    val nextCore = curCore.next.value!!
                    var copyIndex = 0
                    while (copyIndex < curCore.size.value) {
                        when (val curElement = curCore.array[copyIndex].value) {
                            is Frozen -> {
                                nextCore.array[copyIndex].compareAndSet(null, curElement.element)
                                copyIndex++
                                continue
                            }
                            else -> {
                                if (curCore.array[copyIndex].compareAndSet(curElement, Frozen(curElement!!))) {
                                    nextCore.array[copyIndex].compareAndSet(null, curElement)
                                    copyIndex++
                                    continue
                                }
                                else continue
                            }
                        }
                    }
                    core.compareAndSet(curCore, nextCore)
                    continue
                }

                else -> {
                    if (!curCore.array[index].compareAndSet(curCellValue, element)) continue
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

        return when(val curCellValue = curCore.array[index].value) {
            is Frozen -> {
                curCellValue.element as E
            }

            else -> {
                curCellValue as E
            }
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