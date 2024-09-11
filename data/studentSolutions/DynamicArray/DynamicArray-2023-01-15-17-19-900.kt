package mpp.dynamicarray

import kotlinx.atomicfu.*
import java.lang.Integer.min


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
        require(index in 0 until size)
        while (true) {
            if (core.value.size > index) {
                return core.value[index] ?: continue
            }
        }
    }

    override fun put(index: Int, element: E) {
        require(index in 0 until size)
        while (true) {
            val c = core.value
            if (c.size > index) {
                c.set(index, null) ?: continue
                c.set(index, element)
                return
            }
        }
    }

    override fun pushBack(element: E) {
        val index = curSize.getAndIncrement()
        while (true) {
            val c = core.value
            if (c.size > index) {
                if (c.cas(index, null, element)) {
                    return
                }
            } else {
                val newC = Core<E>(c.size * 2)

                if (c.next.compareAndSet(null, newC)) {

                    for (i in 0 until c.size) {
                        while (true) {
                            val e = c.set(i, null)
                            if (e != null) {
                                newC.set(i, null)
                                break
                            }
                        }
                    }

                    core.getAndSet(newC)
                }
            }
        }
    }

    override val size: Int get() = curSize.value
}


private class Core<E>(
    capacity: Int,
) {
    private val array = atomicArrayOfNulls<E>(capacity)
    val next = atomic<Core<E>?>(null)

    val size: Int
        get() = array.size

    operator fun get(index: Int) : E? {
        return array[index].value
    }

    fun set(index: Int, element: E?) : E? {
        return array[index].getAndSet(element)
    }

    fun cas(index: Int, oldElement: E?, newElement: E?) : Boolean {
        return array[index].compareAndSet(oldElement, newElement)
    }

}


private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME