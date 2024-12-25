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

    override fun get(index: Int): E {
        val core = core.value
        val cur = core.get(index)
        val next_core = core.next.value
        if (next_core != null) {
            return next_core.get(index) ?: return cur
        } else {
            return cur
        }
    }

    override fun put(index: Int, element: E) {
        var cur_core = core.value
//        val old_value = cur_core.get(index)
        while (true) {
//            if ( ! cur_core.compareAndSet(index, old_value, element)) {
//                return
//            }
            cur_core.put(index, element)
            cur_core = cur_core.next.value ?: return
        }
    }

    override fun pushBack(element: E) {
        while (true) {
            val cur_core = core.value
            val size = cur_core.size
            if (size < cur_core.capacity) {
                if (cur_core.tryPushBack(size, element)) {
                    return
                }
            } else {
                val next = Core<E>(cur_core.capacity * 2, size)
                cur_core.next.compareAndSet(null, next)
                for (i in 0 until cur_core.capacity) {
                    cur_core.next.value!!.compareAndSet(i, null, cur_core.get(i))
                }
                core.compareAndSet(cur_core, cur_core.next.value!!)
            }
        }
    }

    override val size: Int get() = core.value.size
}

private class Core<E>(
    val capacity: Int,
    size: Int = 0
) {
    private val array = atomicArrayOfNulls<E>(capacity)
    private val _size = atomic(size)
    val next: AtomicRef<Core<E>?> = atomic(null)

    var size: Int = _size.value

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E {
        require(index < size)
        return array[index].value as E
    }

    fun compareAndSet(index: Int, expect: E?, element: E?): Boolean {
        require(index < size)
        return array[index].compareAndSet(expect, element)
    }

    fun put(index: Int, element: E?) {
        require(index < size)
        array[index].lazySet(element)
    }

    fun tryPushBack(index: Int, element: E?): Boolean {
        val cur_size = size
        if (cur_size != index) {
            return false
        }
        if (array[index].compareAndSet(null, element)) {
            println(size)
            size = _size.incrementAndGet()
            return true
        }
        return false

//        require(size < capacity)
//        val cur_size = _size.getAndIncrement()
//        require(index == cur_size)
//        array[cur_size].compareAndSet(null, element)
    }


}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME