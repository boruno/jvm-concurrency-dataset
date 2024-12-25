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

@Suppress("UNCHECKED_CAST")
class DynamicArrayImpl<E> : DynamicArray<E> {
    private val realSize = atomic(0)
    private val core = atomic(Core<E>(INITIAL_CAPACITY))

    override fun get(index: Int): E {
        if (index >= size) throw IllegalArgumentException()
        val core = core.value
        val x = core.get(index)
        if (x != null) return x else throw RuntimeException()
    }

    override fun put(index: Int, element: E) {
        if (index >= size) throw IllegalArgumentException()
        while (true) {
            val core = core.value
            val value = core.get(index)
            if (isNeedToMove(core, value) && isNotNeedToMove(index, core, element, value)) {
                return
            }
        }
    }

    override fun pushBack(element: E) {
        while (true) {
            val size = size
            val core = core.value
            if (core.capacity <= size) {
                move(core)
            } else if (isNotNeedToMove(size, core, element)) {
                realSize.incrementAndGet()
                return
            }
        }
    }

    override val size: Int get() = realSize.value

    private fun isNotNeedToMove(num : Int, core: Core<E>, value: Any?, expect: Any? = null): Boolean =
        core.array[num].compareAndSet(expect, NotNeedToMove(value))

    private fun isNeedToMove(core: Core<E>, value: Any?): Boolean =
        if (value is NeedToMove<*>) {
            move(core)
            false
        } else true

    private fun move(core: Core<E>) {
        core.next.value ?: core.next.compareAndSet(null, Core(2 * core.capacity))
        val next = core.next.value ?: return
        (0..core.capacity-1).forEach { num ->
            var value: Any?

            do value = core.array[num].value
            while (value is NotNeedToMove<*> && !core.array[num].compareAndSet(value, NeedToMove(value.value)))

            if (value is NeedToMove<*>) isNotNeedToMove(num, next, value.value)
            if (value is NotNeedToMove<*>) isNotNeedToMove(num, next, value.value)
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