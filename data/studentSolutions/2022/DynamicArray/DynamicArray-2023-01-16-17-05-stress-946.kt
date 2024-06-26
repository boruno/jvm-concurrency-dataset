package mpp.dynamicarray

import kotlinx.atomicfu.*

data class FixedValue<E>(val value: E)

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
    private val _size = atomic(0)
    private val pointer = atomic(0)
    override val size get() = _size.value

    override fun get(index: Int): E {
        if (index < 0 || index >= size) {
            throw IllegalArgumentException("Index $index out of bounds [0, $size)")
        }
        while (true) {
            return core.value.get(index) ?: continue
        }
    }

    override fun put(index: Int, element: E) {
        if (index < 0 || index >= size) {
            throw IllegalArgumentException("Index $index out of bounds [0, $size)")
        }
        while (true) {
            val curCore = core.value
            val curVal = curCore.array[index].value
            if (curVal is FixedValue<*> || curVal is Broken) {
                continue
            }
            curCore.array[index].compareAndSet(curVal, element)
            return
        }
    }

    override fun pushBack(element: E) {
        var index = 0
        while (core.value.array[index].value != null) {
            index++;
        }
        while (true) {
            var curCore = core.value
            if (index < curCore.capacity.value) {
                    val v = curCore.array[index].value
                    if (curCore.array[index].compareAndSet(null, element)) {
                        _size.getAndIncrement()
                        return
                    }
                    while (curCore.array[index].value is Broken) {
                        curCore = curCore.next.value ?: continue
                        if (curCore.array[index].compareAndSet(null, element)) {
                            _size.getAndIncrement()
                            return
                        }
                    }
            } else {
                resize(curCore)
            }
        }
    }

    object Broken

    private fun resize(oldCore: Core<E>) {
        val newCore = Core<E>(2 * oldCore.capacity.value)

        if (oldCore.next.compareAndSet(null, newCore)) {
            for (i in 0 until oldCore.capacity.value) {
                while (true) {
                    val element = oldCore.array[i].value
                    if (oldCore.array[i].compareAndSet(null, Broken)) {
                        break
                    }

                    when (element) {
                        is FixedValue<*> -> {
                            newCore.array[i].value = element.value as E
                            break
                        }
                        else -> {
                            val e = element as E
                            if (oldCore.array[i].compareAndSet(e, FixedValue(e))) {
                                newCore.array[i].value = e
                                break
                            }
                        }
                    }
                }
            }
        }
        core.compareAndSet(oldCore, newCore)
    }

}

private class Core<E>(
    cap: Int
) {
    val array = atomicArrayOfNulls<Any>(cap)
    val capacity = atomic(cap)
    val next = atomic<Core<E>?>(null)


    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E = when (val a = array[index].value) {
            is FixedValue<*> -> a.value as E
            else -> a as E
        }

}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME