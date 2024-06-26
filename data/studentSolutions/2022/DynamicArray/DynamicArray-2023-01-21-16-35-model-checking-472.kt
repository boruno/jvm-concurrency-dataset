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

@Suppress("UNCHECKED_CAST")
class DynamicArrayImpl<E> : DynamicArray<E> {
    private val realSize = atomic(0)
    private val core = atomic(Core<E>(INITIAL_CAPACITY))

    private fun checkIndex(index: Int) {
        if (index < 0 || index >= realSize.value)
            throw IllegalArgumentException()
    }

    override fun get(index: Int): E {
        checkIndex(index)
        while (true) {
            val core = core.value
            val value = core.get(index)
            if (value is NeedToMove<*>) {
                createMove(core)
            } else if (value is NotNeedToMove<*>){
                return value.value as E
            }
        }
    }

    override fun put(index: Int, element: E) {
        checkIndex(index)
        while (true) {
            val core = core.value
            val value = core.get(index)
            if (value is NeedToMove<*>) {
                createMove(core)
            } else {
                if (core.array[index].compareAndSet(null, NotNeedToMove(value))) {
                    return
                }
            }
        }
    }

    override fun pushBack(element: E) {
        while (true) {
            val size = size
            val core = core.value
            if (core.capacity <= size) {
                createMove(core)
            } else if (core.array[size].compareAndSet(null, NotNeedToMove(element))) {
                realSize.incrementAndGet()
                return
            }
        }
    }

    override val size: Int get() = realSize.value

    private fun createMove(core: Core<E>) {
        move(core)
    }

    private fun move(core: Core<E>) {
        if (core.next.value != null) {
            core.next.compareAndSet(null, Core(2 * core.capacity))
        }
        val next = core.next.value ?: return
        for (i in 0..core.capacity-1) {
            var value: Any?
            do value = core.array[i].value
            while (value is NotNeedToMove<*> && !core.array[i].compareAndSet(value, NeedToMove(value.value)))
            if (value is NeedToMove<*>) {
                next.array[i].compareAndSet(null, NotNeedToMove(value.value))
            }
            if (value is NotNeedToMove<*>) {
                next.array[i].compareAndSet(null, NotNeedToMove(value.value))
            }
        }
        this.core.compareAndSet(core, next)
    }
}

private class NeedToMove<E>(val value: E)
private class NotNeedToMove<E>(val value: E)

private class Core<E>(
    val capacity: Int,
) {
    val array = atomicArrayOfNulls<Any>(capacity)
    val next: AtomicRef<Core<E>?> = atomic(null)

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E {
        return array[index].value as E
    }
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME