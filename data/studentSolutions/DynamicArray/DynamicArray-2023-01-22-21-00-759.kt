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
            val curCore = core.value
            if (curCore.capacity > index) {
                val res = curCore.array[index].value
                if (res is MovedElement) {
                    return curCore.nextCore.value!!.array[index].value?.element ?: return res.element
                }
                return res?.element ?: continue
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
                var oldValue = oldCore.array[index].value
                if (oldValue is MovedElement) {
                    oldValue = oldCore.nextCore.value!!.array[index].value
                    if (oldCore.nextCore.value!!.array[index].compareAndSet(oldValue, Element(element))) return
                } else {
                    if (oldCore.array[index].compareAndSet(oldValue, Element(element))) return
                }
            }
        }
    }

    override fun pushBack(element: E) {
        val newSize = curSize.getAndIncrement()
        while (true) {
            val curCore = core.value
            if (newSize < curCore.capacity) {
                if (curCore.array[newSize].compareAndSet(null, Element(element))) {
                    return
                }
            } else {
                core.compareAndSet(curCore, resize(curCore))
            }
        }
    }

    private fun resize(oldCore: Core<E>): Core<E> {
        val newCore = Core<E>(oldCore.capacity * 2)
        if (oldCore.nextCore.compareAndSet(null, newCore)) {
            for (i in 0 until oldCore.capacity) {
                val oldValue = oldCore.array[i].value
                if (oldValue != null) {
                    newCore.array[i].compareAndSet(null, Element(oldValue.element))
                    oldCore.array[i].compareAndSet(oldValue, MovedElement(oldValue.element))
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
    val size = atomic(0)

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E {
        require(index < size.value)
        return array[index].value as E
    }
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME