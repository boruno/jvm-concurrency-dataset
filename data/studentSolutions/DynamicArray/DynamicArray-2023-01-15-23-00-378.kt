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

class DynamicArrayImpl<E> : DynamicArray<E> {
    private val core = atomic(Core<E>(INITIAL_CAPACITY))
    private val curSize = atomic(0)

    override fun get(index: Int): E {
        require(index in 0 until size)
        while (true) {
            val c = core.value
            if (c.size > index) {
                val e = c[index] ?: continue
                if (e is Moved<E>) {
                    moveArray(c)
                    continue
                }
                return (e as Variable).get()
            }
        }
    }

    override fun put(index: Int, element: E) {
        require(index in 0 until size)
        while (true) {
            val c = core.value
            if (c.size > index) {
                val e = c[index]
                if (e is Moved) {
                    moveArray(c)
                    continue
                }
                if (c.cas(index, e, Variable(element))) {
                    return
                }
            } else {
                moveArray(c)
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
                } else {
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
                val nxt = newC[i]
                when (val e = c[i]) {
                    is Moved -> break
                    else -> {
                        if (newC.cas(i, nxt, e) && c.cas(i, e, Moved())) {
                            break
                        }
                    }
                }

            }
        }

        core.getAndSet(newC)
    }

    override val size: Int get() = curSize.value
}

sealed interface Box<E>

class Moved<E> : Box<E>

class Variable<E>(private val element: E) : Box<E> {
    fun get(): E {
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