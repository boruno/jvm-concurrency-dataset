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


class Element<E>(val element: E, val status: STATUS)
enum class STATUS {
    MOVED, FIX_VALUE, REAL
}

class DynamicArrayImpl<E> : DynamicArray<E> {
    private val core: AtomicRef<Core<E>> = atomic(Core(INITIAL_CAPACITY))

    override fun get(index: Int): E {
        return core.value.get(index)
    }

    override fun put(index: Int, element: E) {
        return core.value.put(index, element)
    }

    override fun pushBack(element: E) {
        while (true) {
            val curCore = core.value
            if (curCore.pushBack(element)) return

            core.compareAndSet(curCore, curCore.move())
        }
    }

    override val size: Int get() {
        return core.value.size
    }
}

private class Core<E>(capacity: Int) {
    private val array: AtomicArray<Element<E>?> = atomicArrayOfNulls(capacity)
    private val next: AtomicRef<Core<E>?> = atomic(this)
    private val size_: AtomicInt = atomic(0)

    fun get(index: Int): E {
        if (index in 0 until size) {
            val elementAtCurIndex = array[index].value!!
            val curCoreNext = next.value

            when (elementAtCurIndex.status) {
                STATUS.REAL -> {
                    return elementAtCurIndex.element
                }
                STATUS.FIX_VALUE -> {
                    return elementAtCurIndex.element
                }
                STATUS.MOVED -> {
                    return curCoreNext!!.get(index)
                }
            }
        } else {
            throw IllegalArgumentException("Index out of bound: index=$index, size=$size")
        }
    }

    fun put(index: Int, value: E) {
        if (index in 0 until size) {
            while (true) {
                val elementAtCurIndex = array[index].value!!
                val curCoreNext = next.value

                when (elementAtCurIndex.status) {
                    STATUS.REAL -> {
                        if (array[index].compareAndSet(elementAtCurIndex, Element(value, STATUS.REAL))) {
                            return
                        }
                    }
                    STATUS.FIX_VALUE -> {
                        curCoreNext!!.array[index].compareAndSet(null, Element(elementAtCurIndex.element, STATUS.REAL))
                        array[index].compareAndSet(elementAtCurIndex, Element(elementAtCurIndex.element, STATUS.MOVED))
                        continue
                    }

                    else -> {
                        curCoreNext!!.put(index, value)
                        return
                    }
                }
            }
        } else {
            throw IllegalArgumentException("Index out of bound: index=$index, size=$size")
        }
    }

    fun pushBack(value: E) : Boolean {
        while (true) {
            val idx = size
            if (idx < array.size) return false

            if (array[idx].compareAndSet(null, Element(value, STATUS.REAL))) {
                size_.compareAndSet(idx, idx + 1)
                return true
            }
            size_.compareAndSet(idx, idx + 1)
        }
    }

    val size: Int = size_.value
    fun move(): Core<E> {
        val newCore = Core<E>(array.size * 2)
        newCore.size_.compareAndSet(0, array.size)

        if (next.value == this) next.compareAndSet(this, newCore)

        for(index in 0 until size) {
            while (true) {
                val oldValue = array[index].value!!

                when (oldValue.status) {
                    STATUS.REAL -> {
                        array[index].compareAndSet(oldValue, Element(oldValue.element, STATUS.FIX_VALUE))
                    }
                    STATUS.FIX_VALUE -> {
                        next.value!!.array[index].compareAndSet(null, Element(oldValue.element, STATUS.REAL))
                        array[index].compareAndSet(oldValue, Element(oldValue.element, STATUS.MOVED))
                        continue
                    }
                    else -> {
                        break
                    }
                }
            }
        }
        return next.value!!
    }
}

private interface Node<E>

private class Common<E>(val value: E): Node<E>
private class Moving<E>(val value: E): Node<E>
private class Moved<E>: Node<E>

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME