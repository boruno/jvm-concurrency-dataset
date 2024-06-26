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

    override fun get(index: Int): E = core.value.get(index).element!!

    override fun put(index: Int, element: E) {
        require(index < size)

        while (true) {
            val currentCore = core.value
            val currentElement = currentCore.get(index)

            if (isElementAssignable(currentElement) && currentCore.casElement(index, currentElement, CommonElement(element)))
                break

            TODO()
        }
    }

    override fun pushBack(element: E) {
        while (true) {
            val currentCore = core.value

            if (currentCore.tryPushBackWithoutExtending(element))
                break
            else currentCore.isExtending.compareAndSet(expect = false, update = true)
        }
    }

    override val size: Int get() = core.value.getSize()

    private fun isElementAssignable(element: BaseElement<E>): Boolean {
        return element !is FixedElement && element !is MovedElement
    }
}

private class Core<E>(
    capacity: Int,
) {
    private val array = atomicArrayOfNulls<BaseElement<E>>(capacity)
    private val _size = atomic(0)

    val next = atomic(null)
    val isExtending = atomic(false)

    fun getSize(): Int {
        return _size.value
    }

    fun getCapacity(): Int {
        return array.size
    }

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): BaseElement<E> {
        require(index < getSize())
        return array[index].value as BaseElement<E>
    }

    fun tryPushBackWithoutExtending(element: E): Boolean {
        if (getSize() >= getCapacity())
            return false

        return array[_size.getAndIncrement()].compareAndSet(null, CommonElement(element))
    }

    fun casElement(index: Int, currentElement: BaseElement<E>, element: BaseElement<E>): Boolean {
        return array[index].compareAndSet(currentElement, element)
    }
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME

// We use this type to indicate base type for an array element's types
private open class BaseElement<E>(open val element: E? = null)
// We use this type to mark an element that is not in progress of moving or moved
private data class CommonElement<E>(override val element: E?) : BaseElement<E>()

// We use this type to mark an element when core transferring is in progress
private data class FixedElement<E>(override val element: E) : BaseElement<E>()

// We use this type to mark an element when core transferring is completed, so the element is moved
private data class MovedElement<E>(override val element: E) : BaseElement<E>()