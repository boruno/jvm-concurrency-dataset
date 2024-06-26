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
        val c = core.value
        if (c.pushBack(element)) {
            core.compareAndSet(c, c.next.value!!)
        }
    }

    override val size: Int get() = core.value.size
}

private class Core<E>(
    capacity: Int,
) {
    private val array = atomicArrayOfNulls<Box<E>>(capacity)
    private val _size = atomic(0)

    val next = atomic<Core<E>?>(null)

    val size: Int
        get() = _size.value

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E {
        require(index < size)
        val ans = array[index].value
        if (ans is Continue<*>) {
            return next.value!!.get(index)
        }
        return (ans as OK<E>).value
    }

    fun put(index: Int, element: E) {
        require(index < size)
        do{
            when(val cur = array[index].value) {
                null -> continue
                is Continue<*> -> {
                    next.value!!.put(index, element)
                    return
                }
                is OK<E> -> {
                    if (array[index].compareAndSet(cur, OK(element))) {
                        return
                    }
                }
            }

        } while (true)
    }

    fun pushBack(element: E): Boolean {
        val index = _size.getAndIncrement()
        if (index >= array.size) {
            constructNext()
            next.value!!.pushBack(element)
            return true
        }
        array[index].getAndSet(OK(element))
        return false
    }

    fun constructNext() {
        var nxt = Core<E>(array.size * 2)
        next.compareAndSet(null, nxt)
        nxt = next.value!!

        for (i in 0 until array.size) {
            do {
                when(val upd = array[i].value) {
                    is Continue<*> -> break
                    is OK<E> -> {
                        nxt.array[i].getAndSet(upd)
                        if (array[i].compareAndSet(upd, Continue())) {
                            nxt._size.incrementAndGet()
                        }
                    }
                    null -> throw IllegalArgumentException("Unreachable")
                }
            } while (true)
        }

    }

}

private sealed interface Box<E>

private data class OK<E>(val value: E) : Box<E>

private class Continue<E> : Box<E>

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME