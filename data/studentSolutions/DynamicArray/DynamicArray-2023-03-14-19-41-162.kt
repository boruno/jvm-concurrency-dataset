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
    abstract class ValueState<E> (val value: E)
    {
        class Moved<E>(value: E) : ValueState<E>(value)
        class Good<E>(value: E) : ValueState<E>(value)
    }

    private val core = atomic(Core<E>(INITIAL_CAPACITY))
    private val _size: AtomicInt = atomic(0)
    private fun CopyItem(currentCore: Core<E>, index: Int)
    {
        while (true)
        {
            val currentItemForCopy = currentCore.array[index].value
            when(currentItemForCopy) {
                is ValueState.Good<E>
                -> currentCore.array[index].compareAndSet(currentItemForCopy, ValueState.Moved<E>(currentItemForCopy.value))
                is ValueState.Moved<E>
                -> {
                    currentCore.nextCore.value!!.array[index].compareAndSet(null, ValueState.Good(currentItemForCopy.value))
                    return
                }
            }
        }
    }

    private fun Grow(currentCore: Core<E>, currentCapacity: Int)
    {
        val nextCore = Core<E>(if (currentCapacity < 16) 16 else currentCapacity * 2)
        currentCore.nextCore.compareAndSet(null, nextCore)
        for (i in 0..currentCapacity - 1)
            CopyItem(currentCore, i)

        core.compareAndSet(currentCore, currentCore.nextCore.value!!)
    }

    override fun get(index: Int): E {
        val currentSize = _size.value
        require(index < currentSize)

        while (true)
        {
            val currentCore = core.value

            when (val currentValue = currentCore.array[index].value) {
                is ValueState.Good<E> -> return currentValue.value
                is ValueState.Moved<E> -> {
                    val nextCore = currentCore.nextCore.value
                    check(nextCore != null)
                    val nextCoreValue = nextCore.array[index].value

                    if (nextCoreValue != null)
                        return nextCoreValue.value
                }
            }
        }
    }

    private fun InsertItem(currentCore: Core<E>, index: Int, element: E) : Boolean
    {
        when (val currentValue = currentCore.array[index].value) {
            is ValueState.Good<E> -> {
                if(currentCore.array[index].compareAndSet(currentValue, ValueState.Good(element)))
                    return true;
            }
            is ValueState.Moved<E> -> {
                val nextCore = currentCore.nextCore.value
                check(nextCore != null)
                val nextCoreValue = nextCore.array[index].value

                if(nextCoreValue is ValueState.Good<E> &&
                    nextCore.array[index].compareAndSet(currentValue, ValueState.Good(element)))
                    return true

            }
        }

        return false
    }

    override fun put(index: Int, element: E) {
        val currentSize = _size.value
        require(index < currentSize)
        do {
            val currentCore = core.value
        } while (!InsertItem(currentCore, index, element))
    }

    override fun pushBack(element: E) {
        while (true)
        {
            val currentCore = core.value
            val currentSize = _size.value
            val currentCapacity = currentCore.capacity

            if(currentSize == currentCapacity) {
                Grow(currentCore, currentCapacity)
                continue
            }

            if(core.value.array[currentSize].compareAndSet(null, ValueState.Good(element)))
            {
                _size.getAndIncrement()
                return
            }
        }
    }

    override val size: Int get() {
        return _size.value
    }
}

private class Core<E>(
    val capacity: Int,
) {
    val array = atomicArrayOfNulls<DynamicArrayImpl.ValueState<E>>(capacity)
    val nextCore: AtomicRef<Core<E>?> = atomic(null)
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME