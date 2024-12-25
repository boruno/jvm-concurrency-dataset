//package mpp.dynamicarray

import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls

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

    override fun get(index: Int): E {
        while (true) {
            val currValue = core.value.get(index)
            if (currValue != null && currValue.putAtomicStatus.value == null) return currValue.elem
        }
    }

    override fun put(index: Int, element: E) {
        if (index < 0 || index < core.value.capacitySumSize) throw IllegalArgumentException()

        while (true) {
            val currentValue = core.value.get(index)
            if (currentValue != null && currentValue.putAtomicStatus.value == null) {
                if (core.value.array.get(index).compareAndSet(currentValue, CoreElementRecreate(element))) {
                    return
                }
            }
        }

    }

    override fun pushBack(element: E) {
        while (true) {
            val currCore = core.value
            val coreSize = currCore.size
            val currCap = currCore.capacitySumSize
            if ((coreSize + 1) >= currCap) {

                for (idx in 0 until coreSize) {
                    while (true) {
                        val currCoreElem = currCore.get(idx)!!
                        if (currCoreElem.putAtomicStatus.compareAndSet(null, Any())) break
                    }
                }

                val newCore = Core<E>(currCap * 2)
                newCore.updMainSize(coreSize)

                for (idx in 0 until coreSize) {
                    val currCoreElem = currCore.get(idx)!!
                    newCore.array.get(idx).compareAndSet(null, CoreElementRecreate(currCoreElem.elem))
                }
                if (newCore.array.get(coreSize).compareAndSet(null, CoreElementRecreate(element))) {
                    newCore.incMainSize()
                    if (core.compareAndSet(currCore, newCore)) return
                }

            } else {
                if (currCore.array.get(coreSize).compareAndSet(null, CoreElementRecreate(element))) {
                    currCore.incMainSize()
                    return
                }
            }
        }
    }

    override val size: Int get() = core.value.size
}

private class CoreElementRecreate<E>(val elem: E) {
    val putAtomicStatus = atomic<Any?>(null)
}

private class Core<E>(
    capacity: Int,
) {
    val array = atomicArrayOfNulls<CoreElementRecreate<E>>(capacity)
    private val _size = atomic(0)
    private val _capSize = atomic(capacity)

    val size: Int = _size.value
    val capacitySumSize: Int = _capSize.value

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): CoreElementRecreate<E>? {
        require(index < size)
        return array[index].value
    }

    fun getAtomic(index: Int): CoreElementRecreate<E>? {
        require(index < size)
        return array[index].value
    }

    fun incMainSize() = _size.incrementAndGet()
    fun updMainSize(updSize: Int) {
        _size.value = updSize
    }
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME