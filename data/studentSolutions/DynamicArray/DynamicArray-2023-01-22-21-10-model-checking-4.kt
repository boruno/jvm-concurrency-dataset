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

    override fun get(index: Int): E = core.value.get(index)

    override fun put(index: Int, element: E) = core.value.put(index, element)

    override fun pushBack(element: E) {
        while (true) {
            val curCore = core.value
            val pushbackResult = curCore.pushBack(element)

            if (pushbackResult != null) {
                core.compareAndSet(curCore, pushbackResult)
            } else {
                return
            }
        }
    }

    override val size: Int get() = core.value._size.value
}

class Core<E>(
    val capacity: Int,
) {
    private val array = atomicArrayOfNulls<Element<E>>(capacity)
    private val next: AtomicRef<Core<E>?> = atomic(null)
    val _size = atomic(0)

    val size: Int = _size.value

    fun get(index: Int): E {
        checkBounds(index)

        while (true) {
            val elementAtIndex = array[index].value!!

            when (elementAtIndex.status) {
                STATUS.REAL -> {
                    return elementAtIndex.element
                }
                STATUS.FIX_VALUE -> {
                    return elementAtIndex.element
                }
                STATUS.MOVED -> {
                    val curCoreNext = next.value
                    if (curCoreNext != null) {
                        return curCoreNext.get(index)
                    } else {
                        continue
                    }
                }
            }
        }
    }

    fun put(index: Int, element: E) {
        checkBounds(index)

        val elementToPut = Element(element, STATUS.REAL)

        while (true) {
            val curElementAtIndex = array[index].value!!


            when (curElementAtIndex.status) {
                STATUS.REAL -> {
                    if (array[index].compareAndSet(curElementAtIndex, elementToPut)) {
                        break
                    }
                }
                STATUS.FIX_VALUE -> {
                    val curCoreNext = next.value!!
                    curCoreNext.array[index].compareAndSet(
                        null,
                        Element(curElementAtIndex.element, STATUS.REAL)
                    )
                    array[index].compareAndSet(
                        curElementAtIndex,
                        Element(curElementAtIndex.element, STATUS.MOVED)
                    )
                    continue
                }
                STATUS.MOVED -> {
                    val curCoreNext = next.value
                    if (curCoreNext != null) {
                        curCoreNext.put(index, element)
                        break
                    } else {
                        continue
                    }
                }
            }
        }
    }

    fun pushBack(element: E): Core<E>? {
        val elementToPush = Element(element, STATUS.REAL)

        while (true) {
            val idx = size

            if (idx < capacity) {
                if (array[idx].compareAndSet(null, elementToPush)) {
                    _size.compareAndSet(idx, idx + 1)
                    return null
                }
                _size.compareAndSet(idx, idx + 1)
            } else {
                return getResizedArray()
            }
        }
    }

    private fun getResizedArray(): Core<E>? {
        val newCore = Core<E>(capacity * 2)
        newCore._size.value = capacity

        next.compareAndSet(null, newCore)

        for (i in 0 until capacity) {
            while (true) {
                val elementInPrevCore = array[i].value ?: break
                val curCoreNext = next.value!!

                when (elementInPrevCore.status) {
                    STATUS.REAL -> {
                        array[i].compareAndSet(
                            elementInPrevCore,
                            Element(elementInPrevCore.element, STATUS.FIX_VALUE)
                        )
                    }

                    STATUS.FIX_VALUE -> {
                        curCoreNext.array[i].compareAndSet(
                            null,
                            Element(elementInPrevCore.element, STATUS.REAL)
                        )
                        array[i].compareAndSet(
                            elementInPrevCore,
                            Element(elementInPrevCore.element, STATUS.MOVED)
                        )
                        continue
                    }

                    STATUS.MOVED -> {
                        break
                    }
                }
            }
        }

        return next.value
    }

    private fun checkBounds(index: Int) {
        if (index < 0 || index >= size) {
            throw IllegalArgumentException("Index out of bound: index=${index}, size=${size}")
        }
    }
}
class Element<E>(val element: E, val status: STATUS)
enum class STATUS {
    MOVED, FIX_VALUE, REAL
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME