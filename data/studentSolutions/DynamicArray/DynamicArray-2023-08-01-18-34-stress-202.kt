package day4

import kotlinx.atomicfu.*

// This implementation never stores `null` values.
class DynamicArray<E: Any> {
    private val core = atomic(Core(capacity = 1)) // Do not change the initial capacity

    private fun createNewCore(oldCore: Core): Core {
        val newCore = Core(oldCore.capacity * 2)
        newCore.size.value = oldCore.size.value
        oldCore.next.compareAndSet(null, newCore)
        return newCore
    }

    private fun copyToNextCore(oldCore: Core, newCore: Core) {
        var index = 0
        while (index < oldCore.size.value) {
            when (val curElement = oldCore.array[index].value) {
                is Frozen -> newCore.array[index].compareAndSet(null, curElement.element)
                else -> oldCore.array[index].compareAndSet(curElement, Frozen(curElement!!))
            }
            index++
        }
    }

    private fun setCore(newCore: Core) {
        val oldCore = core.value
        copyToNextCore(oldCore, newCore)
        core.compareAndSet(oldCore, newCore)
    }

    fun addLast(element: E) {
        while (true) {
            val curCore = core.value
            val nextCore = curCore.next.value
            if (nextCore == null) {
                val curSize = curCore.size.value
                if (curSize == curCore.capacity) {
                    val newCore = createNewCore(curCore)
                    continue
                }
                if (curCore.array[curSize].compareAndSet(null, element) &&
                    curCore.size.compareAndSet(curSize, curSize + 1)) return
            }
            else {
                setCore(nextCore)
            }
        }
    }

    fun set(index: Int, element: E) {
        while (true) {
            val curCore = core.value
            require(index < curCore.size.value) { "index must be lower than the array size" }

            when (val curCellValue = curCore.array[index].value) {
                is Frozen -> {
                    val nextCore = curCore.next.value!!
                    setCore(nextCore)
                    continue
                }
                else -> {
                    if (curCore.array[index].compareAndSet(curCellValue, element)) return
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E {
        val curCore = core.value
        require(index < curCore.size.value) { "index must be lower than the array size" }

        return when(val curCellValue = curCore.array[index].value) {
            is Frozen -> curCellValue.element as E
            else -> curCellValue as E
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
