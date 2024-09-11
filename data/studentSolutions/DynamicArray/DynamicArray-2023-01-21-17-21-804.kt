package mpp.dynamicarray

import kotlinx.atomicfu.*
import java.io.File
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
    private val file = File("out.txt")

    init {
        file.appendText("\n")
    }
    override fun get(index: Int): E = core.value.get(index)

    override fun put(index: Int, element: E) {
        core.value.put(index, element)
        file.appendText("after put " + element + " array[index] " + core.value.get(index) + '\n')
    }

    override fun pushBack(element: E) {
        val curCore = core.value
        val newCore = Core<E>(curCore.capacity + 1, size + 1) //тут ли плюсуем сайз
        if (curCore.next.compareAndSet(null, newCore)) {
            for (i in 0 until curCore.capacity) { // size - 1 ?
                if (curCore.cas(i, null, MOVED)) {
                    continue
                }
                var v = get(i)
                if (v is Fixed) {
                    newCore.cas(i, null, v.value)
                } else {
                    while (v !is Fixed && !curCore.cas(i, v, Fixed(v))) {
                        v = get(i)
                    }
                    if (v is Fixed) {
                        newCore.cas(i, null, v.value)
                    } else {
                        newCore.cas(i, null, v)
                    }
                }
                curCore.put(i, MOVED as E)
            }
            newCore.cas(curCore.capacity, null, element)
            core.compareAndSet(curCore, newCore)
        } else {
            for (i in 0 until size) {
                if (curCore.cas(i, null, MOVED)) {
                    continue
                }
                var v = get(i)
                if (v is Fixed) {
                    curCore.next.value!!.cas(i, null, v.value)
                } else {
                    while (v !is Fixed && !curCore.cas(i, v, Fixed(v))) {
                        v = get(i)
                    }
                    if (v is Fixed) {
                        curCore.next.value!!.cas(i, null, v.value)
                    } else {
                        curCore.next.value!!.cas(i, null, v)
                    }
                }
                curCore.put(i, MOVED as E)
            }
            pushBack(element)
        }
        file.appendText("after pushBack " + element + " size " + core.value.size + '\n')
    }

    override val size: Int get() = core.value.size
}

private class Core<E : Any>(
    public val capacity: Int, elementsCount: Int = 0
) {
    private val array = atomicArrayOfNulls<Any>(capacity)
    private val _size = atomic(elementsCount)

    public val next: AtomicRef<Core<E>?> = atomic(null)

    val size: Int = _size.value

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E {
        require(index < capacity) //?
        val result = array[index].value as E
        if (result == MOVED as E) {
            return next.value!!.get(index)
        }
        if (result is Fixed) {
            return result.value as E
        }
        return result
    }

    @Suppress("UNCHECKED_CAST")
    fun put(index: Int, element: E) {
        require(index < capacity) //?
        do {
            if (cas(index, null, element)) {
                _size.incrementAndGet()
                return
            }
            val v = array[index].value
            if (v == MOVED as E) {
                if (element != MOVED) {
                    next.value!!.put(index, element)
                }
                return
            }
            if (v is Fixed) {
                next.value!!.cas(index, null, v.value)
                array[index].value = MOVED
                next.value!!.cas(index, v.value as E, element)
            }
        } while (!cas(index, v as E, element))
    }

    fun cas(index: Int, expect: E?, update: Any): Boolean {
        return array[index].compareAndSet(expect, update)
    }
}

class Fixed (val value: Any)

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME
const val MOVED = 1351515451