package mpp.dynamicarray

import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls

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


/**
 * @author : Кулешова Екатерина
 */

class DynamicArrayImpl<E> : DynamicArray<E> {
    private val core = atomic(Core<E>(INITIAL_CAPACITY))

    override fun get(index: Int): E {
        require(index in 0 until size)
        return core.value.get(index) ?: throw IllegalArgumentException("no such element")
    }

    override fun put(index: Int, element: E) {
        require(index in 0 until size)
        core.value.put(index, element)
    }

    override fun pushBack(element: E) {
        while (true) {
            val curCore = core.value
            if (curCore.pushBack(element)) break

            val newCore = curCore.refill()
            core.compareAndSet(curCore, newCore)
        }
    }

    override val size: Int get() = core.value.size
}

private class Core<E>(
    capacity: Int,
) {
    private val array = atomicArrayOfNulls<Element<E>>(capacity)
    private val next: AtomicRef<Core<E>?> = atomic(null)

    private val _size = atomic(0)

    val size: Int get() = _size.value

    fun get(index: Int): E? {
        val element = array[index].value
        return if (element is Open<E> || element is Fixed<E>) {
            element.get()!!
        } else {
            next.value?.get(index)
        }
    }

    fun put(index: Int, element: E) {
        while (true) {
            val curValue = array[index].value

            if (curValue is Open<E> && array[index].compareAndSet(curValue, Open(element))) {
                break
            } else if (curValue is Fixed<E> || curValue is Moved<E>) {
                if (next.value != null) {
                    next.value!!.put(index, element)
                    if (array[index].compareAndSet(curValue, Moved())) {
                        break
                    }
                }
            }
        }
    }

    fun pushBack(element: E): Boolean {
        while (true) {
            val oldSize = size
            if (oldSize == array.size) return false

            if (!array[oldSize].compareAndSet(null, Open(element))) continue

            _size.compareAndSet(oldSize, oldSize + 1)
            return true
        }
    }

    fun refill(): Core<E> {
        if (next.compareAndSet(null, Core(array.size * 2))) {
            next.value!!._size.compareAndSet(0, size)
        }

        for (index in 0 until size) {
            while (true) {
                val curValue = array[index].value
                val newValue: Element<E>

                if (curValue is Open<E>) {
                    newValue = Fixed(curValue.get()!!)
                    array[index].compareAndSet(curValue, newValue)

                } else if (curValue is Fixed<E>) {
                    newValue = Open(curValue.get()!!)
                    if (next.value!!.array[index].compareAndSet(null, newValue)) {
                        array[index].compareAndSet(curValue, Moved())
                    }

                } else {
                    break
                }
            }
        }
        return next.value!!
    }
}

private interface Element<E> {
    fun get(): E?
}

private class Open<E>(val element: E) : Element<E> {
    override fun get(): E? = element
}

private class Fixed<E>(val element: E) : Element<E> {
    override fun get(): E? = element
}

private class Moved<E> : Element<E> {
    override fun get(): E? = null
}


private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME