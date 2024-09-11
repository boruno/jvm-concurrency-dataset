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
    private val core : AtomicRef<Core<E>> = atomic(Core<E>(null, INITIAL_CAPACITY, 0 ))

    override fun get(index: Int): E {
		return core.value.array[index].value!!
    }

    override fun put(index: Int, element: E) {
        var corv = core.value
        when {
            index >= corv.sizeAt.value -> {
                throw IllegalArgumentException("Incorrect")
            }
            else -> {
                val corvv = corv.array[index].value
                corv.array[index].compareAndSet(corvv, element)
                while (true) {
                    val corvNext = corv.next.value ?: return
                    corvNext.array[index].getAndSet(corv.array[index].value)
                    corv = corvNext
                }
            }
        }
    }

    override fun pushBack(element: E) {
        while (true) {
            val cnstCore = core.value
            val cnstSize = cnstCore.sizeAt.value
            val cnstCap = cnstCore.capacity
            if (cnstSize >= cnstCap) {
                var biggerCore = Core<E>(null,2 * cnstCap, cnstCap)
                if (cnstCore.next.compareAndSet(null, biggerCore)) {
                    (0 until cnstCore.capacity).forEach { i ->
                        val coreVal = cnstCore.array[i].value
                        if(coreVal != null)
                            biggerCore.array[i].compareAndSet(null, coreVal)
                    }
                    core.compareAndSet(cnstCore, biggerCore)
                } else {
                    biggerCore = cnstCore.next.value!!
                    if (biggerCore!= null) {
                        (0 until cnstCore.capacity).forEach { i ->
                            val coreVal = cnstCore.array[i].value
                            if (coreVal != null)
                                biggerCore.array[i].compareAndSet(null, coreVal)
                        }
                        core.compareAndSet(cnstCore, biggerCore)
                    }
                }
            }
            else {
                cnstCore.array[cnstSize].compareAndSet(null, element)
                cnstCore.sizeAt.compareAndSet(cnstSize, cnstSize + 1)
                return
            }
        }
    }

    override val size: Int get() = core.value.sizeAt.value
}

private class Core<E>(
    next : Core<E>?,
    var capacity: Int,
    size : Int
) {
    val array = atomicArrayOfNulls<E>(capacity)
    val next = atomic(next)
    val sizeAt : AtomicInt = atomic(size)

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E {
        require(index < sizeAt.value)
        return array[index].value as E
    }
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME