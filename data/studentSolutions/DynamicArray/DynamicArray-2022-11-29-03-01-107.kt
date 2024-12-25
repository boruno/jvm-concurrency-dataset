//package mpp.dynamicarray

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
            val currentElement = currentCore.get(index) ?: continue

            if (currentElement is MovedElement) {
                val nextCore = currentCore.next.value

                if (nextCore != null) {
                    nextCore.casSize(expectedSize = 0, newSize = currentCore.getCapacity())
                    core.compareAndSet(currentCore, nextCore)
                }

                continue
            }

            return currentElement.element!!
        }
    }

    override fun put(index: Int, element: E) {
        require(index < size)

        while (true) {
            val currentCore = core.value
            val currentElement = currentCore.get(index)

            if (isElementWritable(currentElement) && currentCore.casElement(index, currentElement, CommonElement(element)))
                break

            val nextCore = currentCore.next.value

            if (nextCore != null) {
                nextCore.casSize(expectedSize = 0, newSize = currentCore.getCapacity())
                core.compareAndSet(currentCore, nextCore)
            }
        }
    }

    override fun pushBack(element: E) {
        while (true) {
            val currentCore = core.value
            val currentSize = currentCore.getSize()

            val isPushBackSuccessful = currentCore.tryPushBackWithoutExtending(currentSize, element)

            if (isPushBackSuccessful)
                return

            val nextCore = currentCore.next.value

            if (nextCore != null) {
                moveElementsToExtendedCore(currentCore, nextCore)

                // In any case we need to retry push back without extending, so just move to the next iteration anyway.
                core.compareAndSet(currentCore, nextCore)
            } else {
                markCoreAsExtending(currentCore)

                val currentCapacity = currentCore.getCapacity()
                val newCapacity = currentCapacity * 2

                val newCore = Core<E>(newCapacity)

                // Let's try again to go through the iteration
                // If there is a failure when pushing without extending we can have this head in use
                // to move elements and then use it to try push back without extending again.
                currentCore.next.compareAndSet(null, newCore)
            }
        }
    }

    override val size: Int get() {
        val currentCore = core.value

        if (CoreState.isActual(currentCore.state.value))
            return currentCore.getSize()

        val updatedCore = currentCore.next.value!!

        return updatedCore.getSize()
    }

    private fun isElementWritable(element: BaseElement<E>?): Boolean {
        return element !is FixedElement && element !is MovedElement
    }

    private fun markCoreAsExtending(core: Core<E>) {
        core.state.compareAndSet(CoreState.Initial, CoreState.InExtending)
    }

    private fun markCoreAsOutdated(core: Core<E>) {
        core.state.compareAndSet(CoreState.InExtending, CoreState.Outdated)
    }

    private fun moveElementsToExtendedCore(currentCore: Core<E>, extendedCore: Core<E>) {
        val currentCapacity = currentCore.getCapacity()
        val currentSize = currentCore.getSize()
        val currentBound = min(currentSize, currentCapacity)

        var index = 0
        while (index < currentCapacity) {
            val movableElement = currentCore.get(index) ?: throw Exception("Illegal value at index $index")
            val fixedElement = movableElement.fixElement()

            // If element is not in processing right now
            if (movableElement is CommonElement) {
                // Fix element so writers couldn't write it in this instance of core
                if (!tryFixElement(currentCore, movableElement, fixedElement, index))
                    continue

                // Copy element to the extended array
                if (!tryCopyElement(extendedCore, movableElement, index))
                    continue

                val currentMovableElement = currentCore.get(index)

                if (currentMovableElement !is FixedElement)
                    continue

                // Mark element as Moved (so it couldn't be used by reader anymore)
                tryMarkElement(currentCore, fixedElement, index)
                index++

                continue
            }

            // If element was fixed by someone else, let's try to help it with the other operations
            if (movableElement is FixedElement) {
                // In case of failure we can just move to the next iteration, because someone is processing this
                // But there is no conditional jumps, because other processing thread can become stunned between
                // copying element and marking an old element as moved, so we need to help with it.
                tryCopyElement(extendedCore, CommonElement(movableElement.element), index)
                tryMarkElement(currentCore, fixedElement, index)
                index++

                continue
            }

            index++
        }

        extendedCore.casSize(expectedSize = 0, newSize = currentCapacity)
        markCoreAsOutdated(currentCore)
    }

    private fun tryFixElement(currentCore: Core<E>, movableElement: BaseElement<E>, fixedElement: FixedElement<E>, index: Int): Boolean {
        return currentCore.casElement(index, movableElement, fixedElement)
    }

    private fun tryCopyElement(newCore: Core<E>, element: CommonElement<E>, index: Int): Boolean {
        return newCore.casElement(index, null, element)
    }

    private fun tryMarkElement(currentCore: Core<E>, fixedElement: FixedElement<E>, index: Int): Boolean {
        return currentCore.casElement(index, fixedElement, MovedElement(fixedElement.element))
    }
}

private class Core<E>(
    capacity: Int,
) {
    private val array = atomicArrayOfNulls<BaseElement<E>?>(capacity)
    private val _size = atomic(0)

    val next: AtomicRef<Core<E>?> = atomic(null)
    val state = atomic(CoreState.Initial)

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

    fun tryPushBackWithoutExtending(index: Int, element: E): Boolean {
        if (index >= getCapacity())
            return false

        val isSuccessful = array[index].compareAndSet(null, CommonElement(element))
        casSize(index, index + 1)

        return isSuccessful
    }

    fun casElement(index: Int, currentElement: BaseElement<E>?, element: BaseElement<E>): Boolean {
        return array[index].compareAndSet(currentElement, element)
    }

    fun casSize(expectedSize: Int, newSize: Int): Boolean {
        return _size.compareAndSet(expectedSize, newSize)
    }
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME

// We use this type to indicate base type for an array element's types
private open class BaseElement<E>(open val element: E?) {
    fun fixElement(): FixedElement<E> {
        return FixedElement(element)
    }
}
// We use this type to mark an element that is not in progress of moving or moved
private data class CommonElement<E>(override val element: E?) : BaseElement<E>(element)

// We use this type to mark an element when core transferring is in progress
private data class FixedElement<E>(override val element: E?) : BaseElement<E>(element)

// We use this type to mark an element when core transferring is completed, so the element is moved
private data class MovedElement<E>(override val element: E?) : BaseElement<E>(element)

private enum class CoreState {
    Initial,
    InExtending,
    Outdated;

    companion object {
        fun isActual(state: CoreState): Boolean = state == Initial || state == InExtending
    }
}