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

class DynamicArrayImpl<E> : DynamicArray<E> {
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
        if (!core.compareAndSet(curCore, core.value.pushBack(element))) {
            pushBack(element)
        }
        file.appendText("after pushBack " + element + " size " + core.value.size + '\n')
    }

    override val size: Int get() = core.value.size
}

private class Core<E>(
    capacity: Int,
) {
    private val array = atomicArrayOfNulls<E>(capacity)
    private val _size = atomic(capacity)

    private final val next: AtomicRef<Core<E>?> = atomic(null)

    val size: Int = if (next.value == null) _size.value else next.value!!.size

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E {
        if (next.value != null) {
            return next.value!!.get(index)
        } else {
            require(index < size)
            return array[index].value as E
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun put(index: Int, element: E) {
        if (next.value != null) {
            next.value?.put(index, element)
            return
        } else {
            require(index < size)
            array[index].value = element
        }
    }

    fun pushBack(element: E): Core<E> {
        if (next.value != null) {
            return next.value!!.pushBack(element)
        } else {
            val newCore = Core<E>(_size.value + 1)
            if (next.compareAndSet(null, newCore)) {
                for (i in 0 until _size.value) {
                    newCore.put(i, get(i))
                }
                newCore.put(_size.value, element)
                return newCore
            } else {
                return next.value!!.pushBack(element)
            }
        }
    }
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME