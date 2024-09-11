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
    private val curSize = atomic(0)

    override fun get(index: Int): E {
        if (index < 0 || index >= size) {
            throw IllegalArgumentException("Index out of bounds")
        }
        while (true) {
            val oldCore = core.value
            val value = oldCore.get(index)

            if (value is MovedElement) {
                core.compareAndSet(oldCore, resize(oldCore))
            } else {
                return value.element
            }
        }
    }

    override fun put(index: Int, element: E) {
        if (index < 0 || index >= size) {
            throw IllegalArgumentException("Index out of bounds")
        }
        while (true) {
            val oldCore = core.value
            if (oldCore.capacity > index) {
                val oldValue = oldCore.array[index].value
                if (oldValue is MovedElement) {
                    core.compareAndSet(oldCore, resize(oldCore))
                } else if (oldCore.array[index].compareAndSet(oldValue, Element(element))) return
            }
        }
    }

    override fun pushBack(element: E) {
        while (true) {
            val curCore = core.value
            val localCurSize = size
            if (localCurSize < curCore.capacity) {
                if (core.value.array[localCurSize].compareAndSet(null, Element(element))) {
                    curSize.incrementAndGet()
                    return
                }
            } else {
                core.compareAndSet(curCore, resize(curCore))
            }
        }
    }

    private fun resize(oldCore: Core<E>): Core<E> {
        val newCore = Core<E>(oldCore.capacity * 2)
        oldCore.nextCore.compareAndSet(null, newCore)

        for (i in 0 until oldCore.capacity) {
            while (true) {
                val oldValue = oldCore.array[i].value ?: break
                if ((oldValue is MovedElement) || oldCore.array[i].compareAndSet(oldValue, MovedElement(oldValue.element))) {
                    oldCore.nextCore.value!!.array[i].compareAndSet(null, Element(oldValue.element))
                    break
                }
            }
        }

        return oldCore.nextCore.value!!
    }


    override val size: Int get() = curSize.value
}

open class Element<E>(val element: E)
class MovedElement<E>(element: E) : Element<E>(element)

class Core<E>(
    val capacity: Int,
) {
    val nextCore: AtomicRef<Core<E>?> = atomic(null)
    val array = atomicArrayOfNulls<Element<E>>(capacity)

    fun get(index: Int): Element<E> {
        return array[index].value!!
    }
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME