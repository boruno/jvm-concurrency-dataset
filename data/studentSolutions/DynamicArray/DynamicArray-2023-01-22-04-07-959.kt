//package mpp.dynamicarray

import kotlinx.atomicfu.*
import java.util.*

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

class DynamicArrayImpl<E : Any> : DynamicArray<E> {
    private val core = atomic(Core<E>(INITIAL_CAPACITY))

    override fun get(index: Int): E = core.value.get(index)

    override fun put(index: Int, element: E) {
        core.value.put(index, element)
    }

    override fun pushBack(element: E) {
        while (true) {
            val curCore = core.value
            while (true) {
                val curSize = curCore._size.value
                if (curSize == curCore.capacity) {
                    break
                }
                if (curCore.cas(curSize, null, element)){
                    curCore._size.compareAndSet(curSize, curSize + 1)
                    return
                }
                curCore._size.compareAndSet(curSize, curSize + 1)
            }
            val newCore = Core<E>(curCore.capacity * 2, size) //тут ли плюсуем сайз
            curCore.next.compareAndSet(null, newCore)
            for (i in 0 until curCore.capacity) {
                while (true) {
                    if (curCore.cas(i, null, MOVED))
                        break
                    val v = curCore.array[i].value
                    if (v == MOVED)
                        break
                    if (v is Fixed) {
                        newCore.cas(i, null, v.value)
                        curCore.put(i, MOVED as E)
                    } else {
                        curCore.cas(i, v as E, Fixed(v))
                    }
                }
            }
            core.compareAndSet(curCore, newCore)
        }
    }

    override val size: Int get() = core.value._size.value
}

private class Core<E : Any>(
    public val capacity: Int, elementsCount: Int = 0
) {
    public val array = atomicArrayOfNulls<Any>(capacity)
    val _size = atomic(elementsCount)

    public val next: AtomicRef<Core<E>?> = atomic(null)

    val size: Int get() {
        return _size.value
    }

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E {
        require(index < _size.value) //?
        val result = array[index].value
        if (result is Fixed) {
            return result.value as E
        }
        if (result == MOVED as E) {
            return next.value!!.get(index)
        }
        return result as E
    }

    @Suppress("UNCHECKED_CAST")
    fun put(index: Int, element: E) {
        require(index < _size.value) //?
        do {
            if (cas(index, null, element)) {
                _size.incrementAndGet()
                return
            }
            val v = array[index].value
            if (v is Fixed) {
                next.value!!.cas(index, null, v.value)
                array[index].value = MOVED
                continue
            }
            if (v == MOVED as E) {
                if (element != MOVED) {
                    next.value!!.put(index, element)
                }
                return
            }
        } while (!cas(index, v as E, element))
    }

    fun cas(index: Int, expect: E?, update: Any): Boolean {
        return array[index].compareAndSet(expect, update)
    }
}

class Fixed (val value: Any)

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME
const val MOVED = 1351515