//package day4

import kotlinx.atomicfu.*

// This implementation never stores `null` values.
class DynamicArray<E: Any> {
    private val core = atomic(Core<E>(capacity = 1)) // Do not change the initial capacity
    private val frozen = Any()

    /**
     * Adds the specified [element] to the end of this array.
     */
    fun addLast(element: E) {
        while(true) {
            val curCore = core.value
            val curSize = curCore.size.value
            val newVal = Elem(element)
            if (curSize < curCore.capacity) {
                if (curCore.array[curSize].compareAndSet(null, newVal)) {
                    curCore.size.compareAndSet(curSize, curSize + 1)
                    return
                }
                curCore.size.compareAndSet(curSize, curSize + 1)
                continue
            }
            curCore.next.compareAndSet(null, Core(capacity = curCore.capacity * 2))
            move()
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
            val new = Elem(element)
            val curVal = curCore.array[index].value!!
            if (curVal.state.value == frozen) {
                val curNextCore = curCore.next.value!!.size.value
                move()
                continue
            }
            if (curCore.array[index].compareAndSet(curVal, new)) {
                val curCoreNext = curCore.next.value ?: return
                if (curCoreNext.array[index].compareAndSet(null, new)) return
            }
            move()
        }
    }

    private fun move() {
        val curCore = core.value
        val curNextCore = curCore.next.value ?: return
        val curSz = curNextCore.size.value
        if (curSz < curCore.capacity) {
            val curVal = curCore.array[curSz].value!!
            curVal.state.compareAndSet(null, frozen)
            curNextCore.array[curSz].compareAndSet(null, Elem(curVal.elem))
            curNextCore.size.compareAndSet(curSz, curSz + 1)
            return
        }
        core.compareAndSet(curCore, curNextCore)
    }

    /**
     * Returns the element located in the cell [index],
     * or throws [IllegalArgumentException] if [index]
     * exceeds the size of this array.
     */
    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E {
        while(true) {
            val curCore = core.value
            val curSize = curCore.size.value
            require(index < curSize) { "index must be lower than the array size" }
            val curVal = curCore.array[index].value!!
            curCore.next.value ?: return curVal.elem
            move()
        }
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