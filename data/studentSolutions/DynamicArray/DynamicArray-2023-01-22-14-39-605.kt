//package mpp.dynamicarray

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
            val curCore = core.value
            val elementAtIndex = curCore.array[index].value!!

            when (elementAtIndex.status) {
                STATUS.REAL -> {
                    return elementAtIndex.element
                }
                STATUS.FIX_VALUE -> {
                    return elementAtIndex.element
                }
                STATUS.MOVED -> {
                    val curCoreNext = curCore.next.value!!
                    core.compareAndSet(curCore, curCoreNext)
                    continue
                }
            }
        }
    }

    override fun put(index: Int, element: E) {
        checkBounds(index)

        val elementToPut = Element(element, STATUS.REAL)

        while (true) {
            val curCore = core.value
            val curElementAtIndex = curCore.array[index].value!!

            when (curElementAtIndex.status) {
                STATUS.REAL -> {
                    if (curCore.array[index].compareAndSet(curElementAtIndex, elementToPut)) {
                        break
                    }
                }
                STATUS.FIX_VALUE -> {
                    val curCoreNext = curCore.next.value!!
                    curCoreNext.array[index].compareAndSet(
                        null,
                        Element(curElementAtIndex.element, STATUS.REAL)
                    )
                    curCore.array[index].compareAndSet(
                        curElementAtIndex,
                        Element(curElementAtIndex.element, STATUS.MOVED)
                    )
                    continue
                }
                STATUS.MOVED -> {
                    val curCoreNext = curCore.next.value!!
                    core.compareAndSet(curCore, curCoreNext)
                    continue
                }
            }
        }
    }

    override fun pushBack(element: E) {
        val elementToPush = Element(element, STATUS.REAL)

        while (true) {
            val localSize = size
            val curCore = core.value
            val curCapacity = curCore.array.size

            if (localSize < curCapacity) {
                if (curCore.array[localSize].compareAndSet(null, elementToPush)) {
                    _size.compareAndSet(localSize, localSize + 1)
                    return
                }
                _size.compareAndSet(localSize, localSize + 1)
            } else {
                resizeArray(curCore, curCapacity)
            }
        }
    }

    private fun resizeArray(prevCore: Core<E>, prevCapacity: Int) {
        val newCore = Core<E>(prevCapacity * 2)

        if (prevCore.next.compareAndSet(null, newCore)) {
            moveElements(prevCore, newCore)
        } else {
            val nextCore = prevCore.next.value
            if (nextCore != null) moveElements(prevCore, nextCore)
        }
        core.compareAndSet(prevCore, newCore)
    }

    fun moveElements(prevCore: Core<E>, newCore: Core<E>) {
        for (i in 0 until prevCore.array.size) {
            while (true) {
                val elementInPrevCore = prevCore.array[i].value ?: break

                when (elementInPrevCore.status) {
                    STATUS.REAL -> {
                        if (prevCore.array[i].compareAndSet(
                                elementInPrevCore,
                                Element(elementInPrevCore.element, STATUS.FIX_VALUE))
                        ) {
                            newCore.array[i].compareAndSet(null, elementInPrevCore)
                            break
                        } else continue
                    }
                    STATUS.FIX_VALUE -> {
                        val curCoreNext = prevCore.next.value!!
                        curCoreNext.array[i].compareAndSet(
                            null,
                            Element(elementInPrevCore.element, STATUS.REAL)
                        )
                        prevCore.array[i].compareAndSet(
                            elementInPrevCore,
                            Element(elementInPrevCore.element, STATUS.MOVED)
                        )
                        continue
                    }
                    else -> break
                }
            }
        }
    }

    private fun checkBounds(index: Int) {
        if (index < 0 || index >= size) {
            throw IllegalArgumentException("Index out of bound: index=${index}, size=${size}")
        }
    }
    override val size: Int get() = _size.value
}

class Core<E>(
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