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
                return core.value[index]?.get() ?: continue
            }
        }
    }

    override fun put(index: Int, element: E) {
        require(index in 0 until size)
        while (true) {
            val c = core.value
            if (c.size > index) {
                c.set(index, null)?.get() ?: continue
                c.set(index, Variable(element))
                return
            }
        }
    }

    override fun pushBack(element: E) {
        val index = curSize.getAndIncrement()
        while (true) {
            val c = core.value
            if (c.size > index) {
                if (c.cas(index, null, Variable(element))) {
                    return
                } else if (c[index] is Const<E>) {
                    moveArray(c)
                }
            } else {
                val newC = Core<E>(c.size * 2)
                c.next.compareAndSet(null, newC)
                moveArray(c)
            }
        }
    }

    private fun moveArray(c: Core<E>) {
        val newC = c.next.value!!
        for (i in 0 until c.size) {
            while (true) {
                val e = c.set(i, Const(null))
                if (e is Const<E>) {
                    break
                }

                if (e != null) {
                    newC.set(i, e)
                    break
                }
            }
        }

        core.getAndSet(newC)
    }

    override val size: Int get() = curSize.value
}

interface Box<E> {
    fun get(): E?
}

class Const<E>(private val element: E?) : Box<E> {
    override fun get(): E? {
        return element
    }
}

class Variable<E>(private val element: E) : Box<E> {
    override fun get(): E {
        return element
    }
}

private class Core<E>(
    private val capacity: Int,
) {
    private val array = atomicArrayOfNulls<Box<E>>(capacity)
    val next = atomic<Core<E>?>(null)

    val size: Int
        get() = capacity

    operator fun get(index: Int): Box<E>? {
        return array[index].value
    }

    fun set(index: Int, element: Box<E>?): Box<E>? {
        return array[index].getAndSet(element)
    }

    fun cas(index: Int, oldElement: Box<E>?, newElement: Box<E>?): Boolean {
        return array[index].compareAndSet(oldElement, newElement)
    }

}


private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME