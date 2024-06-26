package day4

import kotlinx.atomicfu.*

// This implementation never stores `null` values.
class DynamicArray<E: Any> {
    private val core = atomic(Core<E>(capacity = 1)) // Do not change the initial capacity
    private val Frozen = Any()

    /**
     * Adds the specified [element] to the end of this array.
     */
    fun addLast(element: E) {
        val curCore = core.value
        val curSize = curCore.size.value
        val new = Elem(element)
        if (curSize < curCore.capacity) {
            if (curCore.array[curSize].compareAndSet(null, new)) {
                curCore.size.compareAndSet(curSize, curSize + 1)
                return
            }
            curCore.size.compareAndSet(curSize, curSize + 1)
            addLast(element)
            return
        }
        curCore.next.compareAndSet(null, Core(capacity = curCore.capacity * 2))
        val next = curCore.next.value!!
        while (true) {
            val newSize = next.size.value
            val elem = curCore.array[newSize].value!!
            if (newSize >= curCore.capacity) break
            if (curCore.array[newSize].value!!.state.compareAndSet(null, Frozen)) {
                next.array[newSize].compareAndSet(null, elem)
                next.size.compareAndSet(newSize, newSize + 1)
                continue
            }
            next.size.compareAndSet(newSize, newSize + 1)
        }
        core.compareAndSet(curCore, next)
        addLast(element)
    }

    /**
     * Puts the specified [element] into the cell [index],
     * or throws [IllegalArgumentException] if [index]
     * exceeds the size of this array.
     */
    fun set(index: Int, element: E) {
        val curCore = core.value
        val curSize = curCore.size.value
        require(index < curSize) { "index must be lower than the array size" }
        val new = Elem(element)
        if (curCore.array[index].compareAndSet(null, new)) return
        while (true) {
            if (curCore.array[index].value!!.state.compareAndSet(Frozen, null)) {
                val next = curCore.next.value!!
                val newSize = next.size.value
                if (curCore.next.value!!.array[index].compareAndSet(null, new))  {
                    next.size.compareAndSet(newSize, newSize + 1)
                    return
                }
            }
            val curVal = curCore.array[index].value!!
            if (curVal.state.value == Frozen) continue
            if (curCore.array[index].compareAndSet(curVal, new)) {
                return
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
        val curVal = curCore.array[index].value!!
        if (curCore.array[index].value!!.state.compareAndSet(Frozen, null)) {
            val next = curCore.next.value!!
            val newSize = next.size.value
            if (curCore.next.value!!.array[index].compareAndSet(null, curVal))  {
                next.size.compareAndSet(newSize, newSize + 1)
                return curVal.elem
            }
        }
        return curVal.elem
    }

    private class Core<E: Any>(
        val capacity: Int
    ) {
        val array = atomicArrayOfNulls<Elem<E>?>(capacity)
        val size = atomic(0)
        val next = atomic<Core<E>?>(null)
    }

    class Elem <E: Any>(
        val elem: E
    ) {
        val state: AtomicRef<Any?> = atomic(null)
    }
}