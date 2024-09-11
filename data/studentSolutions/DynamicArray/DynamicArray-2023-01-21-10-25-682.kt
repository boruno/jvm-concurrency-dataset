package mpp.dynamicarray

import kotlinx.atomicfu.*
import java.util.concurrent.atomic.AtomicReference

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
    private val _size = atomic(0)
    override fun get(index: Int): E {
        checkBounds(index)

        while (true) {
            val elementAtIndex = core.value.array[index].value!!
            when (elementAtIndex.status) {
                STATUS.REAL -> return elementAtIndex.element
                STATUS.FIX_VALUE -> return elementAtIndex.element
                STATUS.MOVED -> {
                    val curCore = core.value
                    val curCoreNext = core.value.next.value ?: continue
                    core.compareAndSet(curCore, curCoreNext)
                }
            }
        }
    }

    override fun put(index: Int, element: E) {
        checkBounds(index)

        val elementToPut = Element(element, STATUS.REAL)

        while (true) {
            if (core.value.array[index].compareAndSet(null, elementToPut)) {
                return
            } else {
                val elementAtIndex = core.value.array[index].value!!
                when (elementAtIndex.status) {
                    STATUS.REAL -> core.value.array[index].compareAndSet(elementAtIndex, elementToPut)
                    STATUS.FIX_VALUE -> continue
                    STATUS.MOVED -> {
                        val curCore = core.value
                        val curCoreNext = core.value.next.value ?: continue

                        if (core.compareAndSet(curCore, curCoreNext)) {
                            continue
                        }
                    }
                }
            }
        }
    }

    override fun pushBack(element: E) {
        val elementToPush = Element(element, STATUS.REAL)
        val idx = _size.getAndIncrement()

        while (true) {
            val curCore = core.value
            val curCapacity = curCore.array.size

            if (idx < curCapacity) {
                if (curCore.array[idx].compareAndSet(null, elementToPush)) {
                    return
                }
            } else {
                resizeArray(curCore, curCapacity)
            }
        }
    }

    private fun resizeArray(prevCore: Core<E>, prevCapacity: Int) {
        val newCore = Core<E>(prevCapacity * 2)

        if (prevCore.next.compareAndSet(null, newCore)) {
            for (i in 0 until prevCapacity) {
                while (true) {
                    val elementInPrevCore = prevCore.array[i].value ?: break

                    when (elementInPrevCore.status) {
                        STATUS.REAL -> {
                            if (prevCore.array[i].compareAndSet(
                                elementInPrevCore,
                                Element(elementInPrevCore.element, STATUS.FIX_VALUE))
                            ) {
                                newCore.array[i].value = elementInPrevCore
                                break
                            } else continue
                        }
                        else -> break
                    }
                }
            }
            core.value = newCore
        }
    }

    private fun checkBounds(index: Int) {
        if (index < 0 || index >= _size.value) {
            throw IllegalArgumentException("Index out of bound: index=${index}, size=${size}")
        }
    }
    override val size: Int get() = _size.value
}

private class Core<E>(
    capacity: Int,
) {
    val array = atomicArrayOfNulls<Element<E>>(capacity)
    val next: AtomicRef<Core<E>?> = atomic(null)
}
class Element<E>(val element: E, val status: STATUS)
enum class STATUS {
    MOVED, FIX_VALUE, REAL
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME