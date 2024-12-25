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

    override fun get(index: Int): E = core.value.get(index)

    override fun put(index: Int, element: E) {
        var cur_core = core.value
        while (true) {
            val old_value = cur_core.get(index)
            if ( ! cur_core.compareAndSet(index, old_value, element)) {
                return
            }
            cur_core = cur_core.next.value ?: return
        }
    }

    override fun pushBack(element: E) {
        while (true) {
            val cur_core = core.value
            val size = cur_core.size
            if (size < cur_core.capacity) {
                if (cur_core.compareAndSet(size, null, element)) {
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

    val size: Int = _size.value

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

//    fun tryPushBack(element: E?) {
//        while (true) {
//            require(size < capacity)
//            val size = _size.getAndIncrement()
//            if (array[size].compareAndSet(null, element)) {
//                return
//            }
//        }
//    }


}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME