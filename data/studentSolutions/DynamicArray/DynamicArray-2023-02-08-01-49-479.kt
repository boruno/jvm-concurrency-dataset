//package mpp.dynamicarray

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

    override fun get(index: Int): E {
        val localCore = core.value
        if ((index < 0) || (localCore.size.value <= index)) {
            throw IllegalArgumentException("Index is out of bounds")
        }
        return localCore.array[index].value!!.value
    }

    override fun put(index: Int, element: E) {
        var localCore = core.value
        if ((index < 0) || (size <= index)) {
            throw IllegalArgumentException("Index is out of bounds")
        }
        while (true) {
            val oldValue = localCore.array[index].value
            if (oldValue is FixedValue<E>) {
                localCore = localCore.next.value!!
            } else if (oldValue == null || oldValue is FreeValue<E>) {
                if (localCore.array[index].compareAndSet(oldValue, FreeValue<E>(element))) return
            }
        }
    }


    override fun pushBack(element: E) {
        var localCore = core.value
        while (true) {
            val index = localCore.size.value
            if (index < localCore.capacity) { // the element fits -> just put it there
                if (localCore.size.compareAndSet(index, index + 1)) {
                    put(index, element)
                    return
                }
                continue
            }

            val localCoreNextValue = localCore.next.value
            if (localCoreNextValue != null) { // if next exist, there's nothing to do here
                localCore = localCoreNextValue
                continue
            }

            val newCore = Core<E>(2 * localCore.capacity)
            newCore.size.value = index + 1
            newCore.array[index].value = FreeValue(element)
            if (!localCore.next.compareAndSet(null, newCore)) { // if we were to slow, we have to got to the next core
                localCore = localCore.next.value!!
            }

            // now we're responsible for the next core
            for (i in (0 until localCore.capacity)) {
                val oldValue = localCore.array[i].updateAndGet { e -> FixedValue(e!!.value) }
                newCore.array[i].compareAndSet(null, FreeValue(oldValue!!.value))
            }

            while (true) {
                if (core.compareAndSet(localCore, newCore)) return
            }

        }
    }

    override val size: Int get() = core.value.size.value
}

private class Core<E>(
    val capacity: Int,
) {
    val array = atomicArrayOfNulls<Value<E>>(capacity)
    val next = atomic<Core<E>?>(null)
    val size = atomic<Int>(0)
}

private open class Value<E>(open var value: E)
private data class FreeValue<E>(override var value: E) : Value<E>(value)
private data class FixedValue<E>(override var value: E) : Value<E>(value)



private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME
