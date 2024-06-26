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
    private val isTransfering: AtomicBoolean = atomic(false)

    override fun get(index: Int): E = core.value.get(index).element

    override fun put(index: Int, element: E) {

        while (true) {
            val array = core.value
            val newElement = Element(element)
            val value = array.get(index)
            if (!value.isTransfering.value && array.array[index].compareAndSet(value, newElement)) {
                return
            }
        }

    }

    override fun pushBack(element: E) {

        while (true) {
            val array = core.value
            val newElement = Element(element)
            val curSize = size
            if (curSize < array.capacity){
                if (array.array[curSize].compareAndSet(null, newElement)) {
                    array.size.getAndIncrement()
                    return
                }
            } else  {
                if (isTransfering.compareAndSet(false, true)) {
                    val newCore = Core<E>(array.capacity * 2)
                    var idx = 0
                    while (true) {
                        if (idx == curSize) {
                            break
                        }
                        val toTransfer = array.array[idx].value
                        if (array.array[idx].value!!.isTransfering.compareAndSet(false, true)) {
                            newCore.array[idx].compareAndSet(null, toTransfer)
                            newCore.array[idx].value!!.isTransfering.compareAndSet(true, false)
                            idx++
                            newCore.size.getAndIncrement()
                        }
                    }
                    core.compareAndSet(array, newCore)
                    isTransfering.compareAndSet(true, false)
                }
            }
        }

    }

    override val size: Int get() = core.value.size.value
}

private class Core<E>(
    val capacity: Int,
) {
    val array = atomicArrayOfNulls<Element<E>>(capacity)

    val next = atomic(null)
    val size = atomic(0)

    fun get(index: Int): Element<E> {
        require(index < size.value)
        return array[index].value!!
    }
}

private class Element<E>(var element: E) {
    val isTransfering : AtomicBoolean = atomic(false)
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME