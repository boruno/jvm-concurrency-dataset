package mpp.dynamicarray

import kotlinx.atomicfu.*
import kotlin.math.min

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
        require(index < size)

        while (true) {
            val currentCore = core.value
            val currentElement = currentCore.get(index)

            if (currentElement is MovedElement)
                continue

            return currentElement?.element!!
        }
    }

    override fun put(index: Int, element: E) {
        require(index < size)

        while (true) {
            val currentCore = core.value
            val currentElement = currentCore.get(index)

            if (isElementWritable(currentElement) && currentCore.casElement(index, currentElement, CommonElement(element)))
                break
        }
    }

    override fun pushBack(element: E) {

        while (true) {
            val currentCore = core.value

            val pushBackAttemptResult: PushBackResult = currentCore.tryPushBackWithoutExtending(element)

            if (pushBackAttemptResult.isSuccessful) {
                if (currentCore.next.value != null) {
                    val updatedCore = currentCore.next.value!!
                    updatedCore.copyElement(pushBackAttemptResult.index, element)
                }
                break
            } else if (!currentCore.isExtending.value) {
                markCoreAsExtending(currentCore)
            }

            val currentCapacity = currentCore.getCapacity()
            val newCapacity = currentCapacity * 2

            val newCore = Core<E>(newCapacity)

            if (!currentCore.next.compareAndSet(null, newCore))
                continue

            val currentSize = currentCore.getSize()
            val currentCoreBound = min(currentSize, currentCapacity)

            for (index in 0 until currentCoreBound) {
                // Fix element so writers couldn't write it in this instance of core
                val movableElement = currentCore.get(index)
                val fixedElement = movableElement?.fixElement() ?: FixedElement<E>(null)

                currentCore.casElement(index, movableElement, fixedElement)

                // Copy element to the extended array
                newCore.copyElement(movableElement?.element)

                // Mark element as Moved (so it couldn't be used by reader anymore)
                currentCore.casElement(index, movableElement, MovedElement(movableElement?.element))
            }

            // TODO: change order of these calls?
            core.compareAndSet(currentCore, newCore)
            markCoreAsNotExtending(currentCore)
        }
    }

    override val size: Int get() {
        val currentCore = core.value

        if (currentCore.next.value == null)
            return currentCore.getSize()

        if (currentCore.isExtending.value)
            return currentCore.getSize()

        val updatedCore = currentCore.next.value!!

        // if (updatedCore.getSize() < updatedCore.getCapacity())
        return updatedCore.getSize()
    }

    private fun isElementWritable(element: BaseElement<E>?): Boolean {
        return element !is FixedElement && element !is MovedElement
    }

    private fun markCoreAsExtending(core: Core<E>) {
        core.isExtending.compareAndSet(expect = false, update = true)
    }

    private fun markCoreAsNotExtending(core: Core<E>) {
        core.isExtending.compareAndSet(expect = true, update = false)
    }
}

private class Core<E>(
    capacity: Int,
) {
    private val array = atomicArrayOfNulls<BaseElement<E>?>(capacity)
    private val _size = atomic(0)

    val next: AtomicRef<Core<E>?> = atomic(null)
    val isExtending = atomic(false)

    fun getSize(): Int {
        return _size.value
    }

    fun getCapacity(): Int {
        return array.size
    }

    fun get(index: Int): BaseElement<E>? {
        require(index < getSize())
        return array[index].value
    }

    fun tryPushBackWithoutExtending(element: E): PushBackResult {
        val pushIndex = _size.getAndIncrement()

        if (pushIndex >= getCapacity())
            return PushBackResult(isSuccessful = false, index = pushIndex)

        if (!array[pushIndex].compareAndSet(null, CommonElement(element)))
            return PushBackResult(isSuccessful = false, index = pushIndex)

        return PushBackResult(isSuccessful = true, index = pushIndex)
    }

    fun copyElement(element: E?) {
        array[_size.getAndIncrement()].compareAndSet(null, CommonElement(element))
    }

    fun copyElement(index: Int, element: E?) {
        array[index].compareAndSet(null, CommonElement(element))
    }

    fun casElement(index: Int, currentElement: BaseElement<E>?, element: BaseElement<E>): Boolean {
        return array[index].compareAndSet(currentElement, element)
    }
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME

// We use this type to indicate base type for an array element's types
private open class BaseElement<E>(open val element: E? = null) {
    fun fixElement(): FixedElement<E> {
        return FixedElement(element)
    }
}
// We use this type to mark an element that is not in progress of moving or moved
private data class CommonElement<E>(override val element: E?) : BaseElement<E>()

// We use this type to mark an element when core transferring is in progress
private data class FixedElement<E>(override val element: E?) : BaseElement<E>()

// We use this type to mark an element when core transferring is completed, so the element is moved
private data class MovedElement<E>(override val element: E?) : BaseElement<E>()

// We use this type to provide detailed information about push back without extending attempt
private data class PushBackResult(val isSuccessful: Boolean, val index: Int)