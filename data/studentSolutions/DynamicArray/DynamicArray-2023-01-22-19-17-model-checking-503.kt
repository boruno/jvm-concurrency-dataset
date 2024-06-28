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

class DynamicArrayImpl<E> : DynamicArray<E> {
    private val core = atomic(Core<E>(INITIAL_CAPACITY))

    override fun get(index: Int): E {
        require(index in 0 until size)
        return core.value.get(index)
    }

    override fun put(index: Int, element: E) {
        require(index in 0 until size)
        core.value.put(index, element)
    }

    override fun pushBack(element: E) {
        while (true) {
            val c = core.value
            if (c.placeBack(element))
                return
            core.compareAndSet(c, c.ensureCapacity())
        }
    }

    override val size: Int get() = core.value.size
}

private class Core<E>(
    val capacity: Int
) {
    private val array = atomicArrayOfNulls<Any?>(capacity)
    private val next: AtomicRef<Core<E>?> = atomic(null)
    private val _size = atomic(capacity / 2) // toDo change with correct init value

    private val MOVED = Any()

    val size: Int get() = _size.value

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E {
        while (true) {
            when (val e = array[index].value ) {
                is Reserved<*> -> move(index)
                MOVED -> return next.value!!.get(index)
                else -> return e as E
            }
        }
    }

    fun put(index: Int, element: E) {
        while (true) {
            when (val e = array[index].value ) {
                is Reserved<*> -> move(index)
                MOVED -> next.value!!.put(index, element)
                else -> if (array[index].compareAndSet(e, element)) break
            }
        }
    }

    private fun move(index: Int) {
        val e = array[index].value
        if (e is Reserved<*>) {
            next.value!!.array[index].compareAndSet(null, e.element)
            array[index].compareAndSet(e, MOVED)
        }
    }

    fun placeBack(element: E): Boolean {
        while (true) {
            val currentSize = size
            if (currentSize == capacity) {
                return false
            } else {
                if (array[currentSize].compareAndSet(null, element)) {
                    _size.compareAndSet(currentSize, currentSize + 1)
                    return true
                } else {
                    _size.compareAndSet(currentSize, currentSize + 1)
                }
            }
        }
    }

    fun ensureCapacity(): Core<E> {
        next.compareAndSet(null, Core(capacity * 2))

        for (i in 0 until capacity) {
            while (true) {
                when (val e = array[i].value) {
                    is Reserved<*> -> move(i)
                    MOVED -> break
                    else -> array[i].compareAndSet(e, Reserved(e))
                }
            }
        }

        return next.value!!
    }
}

private class Reserved<E>(val element: E)

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME