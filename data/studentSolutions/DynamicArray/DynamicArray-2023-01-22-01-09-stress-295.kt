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

class DynamicArrayImpl<E> : DynamicArray<E> {
    private val core = atomic(Core<E>(INITIAL_CAPACITY))
    private val sizeAt = atomic(0)

    override fun get(index: Int): E {
        if (index >= size)
            throw IllegalArgumentException("Incorrect index")
        return core.value.array[index].value!!.first
    }

    override fun put(index: Int, element: E) {
        if (index >= size)
            throw IllegalArgumentException("Incorrect index")
        while (true) {
            val coreVal = core.value.array[index].value
            if (core.value.array[index].compareAndSet(coreVal, Pair(element, false))
                && !coreVal!!.second)
                return
        }
    }

    override fun pushBack(element: E) {
        while (true) {
            val cnstSize = sizeAt.value
            val cnstCore = core.value
            val cnstCoreSize = cnstCore.capacity
            if (cnstSize + 1 > cnstCoreSize) {
                val biggerCore = Core<E>(2 * cnstCoreSize)
                var check = false;
                for (i in 0 until  cnstCoreSize) {
                    val coreVal = cnstCore.array[i].value
                    if (coreVal == null) continue
                    if (!biggerCore.array[i].compareAndSet(coreVal, Pair(coreVal.first, true))) {
                        check = true
                        break
                    }
                    if (check || !core.compareAndSet(cnstCore, biggerCore))
                        continue
                }
            }
            if (cnstSize < core.value.capacity && core.value.array[cnstSize].compareAndSet(null, Pair(element, false))) {
                sizeAt.incrementAndGet()
                return
            }
        }
    }

    override val size: Int get() = sizeAt.value
}

private class Core<E>(capacity: Int) {
    val capacity = capacity
    val array = atomicArrayOfNulls<Pair<E, Boolean>>(capacity)
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME