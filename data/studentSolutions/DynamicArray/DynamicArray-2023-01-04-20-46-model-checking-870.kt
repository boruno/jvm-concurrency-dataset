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
    private val realSize = atomic(0)

    override fun get(index: Int): E {
        require(index < realSize.value)
        return core.value.get(index)
    }

    override fun put(index: Int, element: E) {
        require(index < realSize.value)
        core.value.put(index, element)
    }

    override fun pushBack(element: E) {
        val c = core.value
        if (c.pushBack(element)) {
            core.compareAndSet(c, c.next.value!!)
        }
        realSize.incrementAndGet()
    }

    override val size: Int get() = realSize.value
}

/*
Init part:
[pushBack(4): void]
Parallel part:
| pushBack(3): void | pushBack(2): void | pushBack(1): void |
Post part:
[put(3, -9): IllegalArgumentException]
 */

private class Core<E>(
    capacity: Int,
) {
    private val array = atomicArrayOfNulls<Box<E>>(capacity)
    private val _size = atomic(0)
    val next = atomic<Core<E>?>(null)

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E {
//        require(index < size)
        var i = index
        while (true) {
            return when (val ans = array[i].value) {
                is Continue<E> -> next.value!!.get(index)
                is OK<E> -> ans.value
                is Const<E> -> ans.value
                null -> {
                    i++
                    continue
                }
            }
        }
    }

    fun put(index: Int, element: E) {
//        require(index < size)
        do {
            if (index >= array.size) {
                constructNext()
                moveElements()
                next.value!!.put(index, element)
                return
            }
            when (val cur = array[index].value) {
                null -> throw IllegalArgumentException(_size.value.toString())
                is Continue<*> -> {
                    next.value!!.put(index, element)
                    return
                }
                is OK<E> -> {
                    if (array[index].compareAndSet(cur, OK(element))) {
                        return
                    }
                }
                is Const<E> -> {
                    moveElements()
                }
            }

        } while (true)
    }

    private fun putNull(index: Int, element: E) {
        do {
            when (val cur = array[index].value) {
                is Continue<*> -> {
                    next.value!!.putNull(index, element)
                    return
                }
                is OK<E>, is Const<E> -> throw RuntimeException(cur.toString())
                null -> {
                    if (array[index].compareAndSet(null, OK(element))) {
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
            moveElements()
            next.value!!.pushBack(element)
            return true
        }
        do {
            when (val a = array[index].value) {
                is Continue<E> -> {
                    next.value!!.putNull(index, element)
                    break
                }
                is OK<E>, is Const<E> -> throw RuntimeException()
                null -> {
                    if (array[index].compareAndSet(null, OK(element))) {
                        break
                    }
                }
            }
        } while (true)

        return false

    }

    fun constructNext() {
        val nxt = Core<E>(array.size * 2)
        nxt._size.getAndSet(array.size)
        next.compareAndSet(null, nxt)
    }

    fun moveElements() {
        val nxt = next.value!!

        for (i in 0 until array.size) {
            do {
                when (val upd = array[i].value) {
                    is Continue<*> -> break
                    is OK<E> -> array[i].compareAndSet(upd, Const(upd.value))
                    is Const<E> -> {
                        nxt.array[i].compareAndSet(null, OK(upd.value))
                        array[i].compareAndSet(upd, Continue())
                    }
                    null -> array[i].compareAndSet(upd, Continue())

                }
            } while (true)
        }
    }

}

private sealed interface Box<E>

private data class OK<E>(val value: E) : Box<E>

private class Continue<E> : Box<E>

private data class Const<E>(val value: E) : Box<E>

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME