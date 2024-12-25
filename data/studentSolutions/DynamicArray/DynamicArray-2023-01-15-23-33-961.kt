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
                return when (val e = c[index]) {
                    is Moved -> {
                        moveArray(c)
                        continue
                    }
                    null -> throw IllegalArgumentException()
                    is Variable -> e.get()
                }
            } else if (c.next.value != null) {
                moveArray(c)
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
                c.next.compareAndSet(null, Core(c.size * 2))
                moveArray(c)
            }
        }
    }

    private fun moveArray(c: Core<E>) {
        for (i in 0 until c.size) {
            while (true) {
                val nxt = c.next.value!!.array[i].value
                when (val e = c[i]) {
                    is Moved -> break
                    else -> {
                        if (c.next.value!!.array[i].compareAndSet(nxt, e) && c.array[i].compareAndSet(e, Moved())) {
                            break
                        }
                    }
                }

            }
        }
        core.compareAndSet(c, c.next.value!!)
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
    val array = atomicArrayOfNulls<Box<E>>(capacity)
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