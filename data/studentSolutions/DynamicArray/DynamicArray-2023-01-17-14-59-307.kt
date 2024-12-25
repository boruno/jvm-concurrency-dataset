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

    override fun get(index: Int): E = core.value.get(index)

    override fun put(index: Int, element: E) {
        require(index < size)
        var coreValue: Core<E>? = core.value

        while (true) {
            coreValue!!.array[index].getAndSet(element)
            coreValue = coreValue.next.value

            if (coreValue == null)
                return
        }
    }

    override fun pushBack(element: E) {
        while (true) {
            val coreValue = core.value
            val curSize = size
            val curCapacity = core.value.getCapacity

            if (curSize < curCapacity) {
                if (!coreValue.array[size].compareAndSet(null, element)) {
                    coreValue.size.compareAndSet(size, size + 1)
                    continue
                }

                coreValue.size.compareAndSet(size, size + 1)
                return
            } else {
                val next = Core<E>(curCapacity * 2)
                next.size.compareAndSet(0, size)
                coreValue.next.compareAndSet(null, next)

                for (i in 0 until curCapacity)
                    coreValue.next.value!!.array[i].compareAndSet(null, coreValue.array[i].value)

                this.core.compareAndSet(coreValue, coreValue.next.value!!)
                continue
            }
        }
    }

    override val size: Int get() = core.value.getSize

    private class Core<E>(capacity: Int) {
        val array = atomicArrayOfNulls<E>(capacity)
        val capacity = atomic(capacity)
        val getCapacity = this.capacity.value
        val size = atomic(0)
        val next: AtomicRef<Core<E>?> = atomic(null)
        val getSize: Int = size.value

        @Suppress("UNCHECKED_CAST")
        fun get(index: Int): E {
            require(index < getSize)
            return array[index].value!!
        }
    }
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME