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
    private val realSize = atomic(0)

    override fun get(index: Int): E {
        if (index >= size) throw IllegalArgumentException()
        val cor = core.value
        return cor.get(index)
    }

    override fun put(index: Int, element: E) {
        if (index >= size) throw IllegalArgumentException()
        val cor = core.value
        cor.getAndSet(index, element)
    }

    override fun pushBack(element: E) {
        while (true) {
            val size = size
            val cor = core.value
            if (cor.capacity <= size){
                cor.next.value ?: cor.next.compareAndSet(null, Core(2*cor.capacity))
                val next = cor.next.value ?: return
                (0..cor.capacity-1).forEach { num ->
                    var value: Any?
                    do value = cor.array[num].value
                    while (value is NotNeedToMove<*> && !cor.array[num].compareAndSet(value as E?, value.value as E?))
                    if (value is NeedToMove<*>) next.array[num].compareAndSet(null, value.value as E?)
                    if (value is NotNeedToMove<*>) next.array[num].compareAndSet(null, value.value as E?)
                }
                this.core.compareAndSet(cor, next)
            } else if (cor.array[size].compareAndSet(null, element)) {
                realSize.incrementAndGet()
                return
            }
        }
    }

    override val size: Int get() = realSize.value
}

private class NeedToMove<E>(val value: E)
private class NotNeedToMove<E>(val value: E)

private class Core<E>(
    val capacity: Int,
) {
    val array = atomicArrayOfNulls<E>(capacity)
    private val _size = atomic(0)
    val next: AtomicRef<Core<E>?> = atomic(null)

    val size: Int = _size.value

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E {
        return array[index].value as E
    }

    @Suppress("UNCHECKED_CAST")
    fun getAndSet(index: Int, element: E?) {
        array[index].getAndSet(element)
    }
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME