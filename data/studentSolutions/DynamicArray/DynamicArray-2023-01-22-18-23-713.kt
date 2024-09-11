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

open class Holder<E>(val element: E)

class Labeled<E>(element: E): Holder<E>(element)


class DynamicArrayImpl<E> : DynamicArray<E> {
    private val core = atomic(Core<E>(INITIAL_CAPACITY))
    private val _size = atomic(0)

    override fun get(index: Int): E {
        require(index < size)
        return core.value.get(index)!!
    }

    override fun put(index: Int, element: E) {
        require(index < size)
        core.value.set(index, element)
    }

    override fun pushBack(element: E) {
        while (true) {
            val oldCore = core.value
            val oldSize = _size.value
            if (oldSize >= oldCore.capacity) {
                oldCore.moveToNewCore()
                core.compareAndSet(oldCore, oldCore.next)
                continue
            }

            if (!core.value.casElement(oldSize, null, Holder(element))) {
                _size.compareAndSet(oldSize, oldSize + 1)
                continue
            }
            _size.compareAndSet(oldSize, oldSize + 1)
            break
        }
    }

    override val size: Int get() = _size.value
}

private class Core<E>(
    val capacity: Int,
) {
    private val array = atomicArrayOfNulls<Holder<E>>(capacity)
    private val newCore: AtomicRef<Core<E>?> = atomic(null)

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E? {
        val got = array[index].value
        if (got is Labeled) {
            return newCore.value!!.get(index) ?: got.element
        }
        return got!!.element
    }

    fun moveToNewCore() {
        newCore.compareAndSet(null, Core(2 * capacity))
        for (i in 0 until capacity) {
            while (true) {
                val oldVal = array[i].value!!
                if (oldVal is Labeled) {
                    newCore.value!!.casElement(i, null, Holder(oldVal.element))
                    continue
                } else if (casElement(i, oldVal, Labeled(oldVal.element))) {
                    newCore.value!!.casElement(i, null, oldVal)
                    break
                }
            }
        }
    }

    val next get() = newCore.value!!

    fun casElement(index: Int, expected: Holder<E>?, update: Holder<E>?): Boolean {
        if (index >= capacity) {
            return false
        }
        return array[index].compareAndSet(expected, update)
    }

    fun set(index: Int, element: E) {
        do {
            val got = array[index].value
            if (got is Labeled) {
                newCore.value!!.set(index, element)
                break
            }
        } while (!casElement(index, got!!, Holder(element)))
    }
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME