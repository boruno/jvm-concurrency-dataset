package mpp.dynamicarray

import kotlinx.atomicfu.*
import java.util.concurrent.atomic.AtomicMarkableReference
import java.util.concurrent.atomic.AtomicReferenceArray

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
    private val migrate = atomic(false)

    override fun get(index: Int): E {
        checkIndex(index)
        return core.value.array[index].value!!.element
    }

    override fun put(index: Int, element: E) {
        checkIndex(index)
        val newValue = Ordinary(element)
        while (true) {
            val curList = core.value
            val curValue = curList.array[index].value
            if (curValue is Ordinary && curList.array[index].compareAndSet(curValue, newValue)) {
                return
            }
        }
    }

    private fun checkIndex(index: Int) {
        if (index !in 0 until size) {
            throw IllegalArgumentException("Index $index out of range")
        }
    }

    override fun pushBack(element: E) {
        val newElement = Ordinary(element)
        while (true) {
            val curList = core.value
            val nextEmpty = curList.nextEmpty.value
            if (curList.capacity > nextEmpty) {
                if (curList.array[nextEmpty].compareAndSet(null, newElement)) {
                    curList.nextEmpty.getAndIncrement()
                    return
                }
            } else if (migrate.compareAndSet(expect = false, update = true)) {
                var pos = 0
                val newCapacity = 2 * curList.capacity
                val tmpList = Core<E>(newCapacity, nextEmpty)
                while (nextEmpty != pos) {
                    val curVal = curList.array[pos].value
                    if (curList.array[pos].compareAndSet(curVal, Relocated(curVal!!.element))) {
                        tmpList.array[pos++].value = curVal
                    }
                }
                core.compareAndSet(curList, tmpList)
                migrate.compareAndSet(expect = true, update = false)
            }
        }
    }

    override val size: Int
        get() {
            return core.value.nextEmpty.value
        }
}

abstract class Shell<E>(val element: E)
class Ordinary<E>(element: E) : Shell<E>(element)
class Relocated<E>(element: E) : Shell<E>(element)

private class Core<E>(
    val capacity: Int,
    nextEmpty: Int = 0
) {
    val nextEmpty = atomic(nextEmpty)
    val array = atomicArrayOfNulls<Shell<E>>(capacity)
}


private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME