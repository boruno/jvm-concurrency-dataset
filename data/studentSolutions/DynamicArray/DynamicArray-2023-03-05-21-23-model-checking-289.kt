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
    private val core: AtomicRef<Core<E>> = atomic(Core(INITIAL_CAPACITY, 0))

    override fun get(index: Int): E {
        if (index < 0 || index >= size) throw IllegalArgumentException("index out of bound")

        while (true) {
            return when (val oldValue = core.value.array[index].value!!) {
                is Moved<E> -> {
                    var cur = core.value
                    while (cur.next.value != cur) {
                        cur = cur.next.value
                    }
                    cur.array[index].value?.let {
                        return it.element
                    }
                    continue
                }

                else -> oldValue.element
            }
        }
    }

    override fun put(index: Int, element: E) {
        return core.value.put(index, element)
    }

    override fun pushBack(element: E) {
        while (true) {
            val curCore = core.value
            if (curCore.pushBack(element)) return

            core.compareAndSet(curCore, curCore.move())
        }
    }

    override val size: Int
        get() {
            return core.value.size
        }
}

private class Core<E>(capacity: Int, curSize: Int) {
    val array: AtomicArray<Wrapper<E>?> = atomicArrayOfNulls(capacity)
    val next: AtomicRef<Core<E>> = atomic(this)
    val size_: AtomicInt = atomic(curSize)

    fun put(index: Int, value: E) {
        checkIndex(index)

        while (true) {
            when (val oldValue = array[index].value!!) {
                is Common<E> -> {
                    if (array[index].compareAndSet(oldValue, Common(value))) break
                }

                is Moving<E> -> {
                    next.value.array[index].compareAndSet(null, Common(oldValue.element))
                    array[index].compareAndSet(oldValue, Moved(oldValue.element))
                    continue
                }

                else -> {
                    continue
                }
            }
        }
    }

    fun pushBack(value: E): Boolean {
        while (true) {
            val oldSize = size
            if (oldSize == array.size) return false

            if (array[oldSize].compareAndSet(null, Common(value))) {
                size_.compareAndSet(oldSize, oldSize + 1)
                return true
            }
            size_.compareAndSet(oldSize, oldSize + 1)
        }
    }

    val size: Int
        get() {
            return size_.value
        }

    fun move(): Core<E> {
        if (next.value == this) next.compareAndSet(this, Core(array.size * 2, size))

        for (index in 0 until size) {
            while (true) {
                when (val oldValue = array[index].value!!) {
                    is Common<E> -> {
                        array[index].compareAndSet(oldValue, Moving(oldValue.element))
                    }

                    is Moving<E> -> {
                        next.value.array[index].compareAndSet(null, Common(oldValue.element))
                        array[index].compareAndSet(oldValue, Moved(oldValue.element))
                        continue
                    }

                    else -> {
                        break
                    }
                }
            }
        }
        return next.value
    }

    private fun checkIndex(index: Int) {
        if (index < 0 || index >= size) throw IllegalArgumentException("index out of bound")
    }
}

private abstract class Wrapper<E>(val element: E)

private class Common<E>(element: E) : Wrapper<E>(element = element)
private class Moving<E>(element: E) : Wrapper<E>(element = element)
private class Moved<E>(element: E) : Wrapper<E>(element = element)

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME