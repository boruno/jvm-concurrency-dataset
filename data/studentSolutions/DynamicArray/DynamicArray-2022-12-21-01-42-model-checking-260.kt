package mpp.dynamicarray

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls

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
    private val core = atomic(Core<Pair<Status, E>>(INITIAL_CAPACITY))
    private val new_core = atomic(Core<Pair<Status, E>>(INITIAL_CAPACITY))
    private val capacity = atomic(INITIAL_CAPACITY)
    private val idx = atomic(INITIAL_CAPACITY)

    private enum class Status {
        READY, MODIFIED
    }

    override fun get(index: Int): E {
        var cur_value = core.value.get(index)!!
        if (cur_value.first == Status.MODIFIED && new_core.value.size > index) {
            cur_value = new_core.value.get(index)!!
        }
        return cur_value.second
    }

    override fun put(index: Int, element: E) {
        while (true) {
            val s = core.value.size
            var cur_value = core.value.get(index)!!
            if (cur_value.first == Status.READY && core.value.cas(index, cur_value, Pair(Status.READY, element))) {
                break
            } else if (cur_value.first == Status.MODIFIED && new_core.value.size > index) {
                cur_value = new_core.value.get(index)!!
                if (new_core.value.cas(index, cur_value, Pair(Status.READY, element)) && s == core.value.size) {
                    break
                }
            } else if (cur_value.first == Status.MODIFIED && new_core.value.add(index, Pair(Status.READY, element))) {
                break
            }
        }
    }

    override fun pushBack(element: E) {
        while (true) {
            val cur_capacity = core.value.capacity
            if (size == cur_capacity) {
                incCapacity(cur_capacity)
            }
            if (core.value.add(size, Pair(Status.READY, element))) {
                break
            }
        }
    }

    fun incCapacity(cur_capacity: Int) {
        capacity.compareAndSet(cur_capacity, cur_capacity * 2)
        val core_value = new_core.value
        if (cur_capacity == core_value.capacity) {
            new_core.compareAndSet(core_value, Core(cur_capacity * 2))
        }
        update(cur_capacity)
    }

    fun update(cur_capacity: Int) {
        var i: Int
        while (true) {
            i = idx.value - cur_capacity
            if (i >= cur_capacity) {
                break
            }

            var now: Pair<Status, E>
            while (true) {
                now = core.value.get(i)!!
                if (now.first == Status.MODIFIED || core.value.cas(i, now, Pair(Status.MODIFIED, now.second))) {
                    break
                }
            }

            new_core.value.add(i, Pair(Status.READY, now.second))

            idx.compareAndSet(i + cur_capacity, i + cur_capacity + 1)
        }

        val core_value = core.value
        if (core_value.get(cur_capacity - 1)!!.first == Status.MODIFIED && cur_capacity == core_value.size) {
            core.compareAndSet(core_value, new_core.value)
        }
    }

    override val size: Int get() = core.value.size
}

private class Core<E>(
    val capacity: Int,
) {
    private val array = atomicArrayOfNulls<E>(capacity)
    private val _size = atomic(0)

    val size: Int get() = _size.value

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E? {
        require(index < size)
        return array[index].value
    }

    fun cas(index: Int, expect: E?, element: E?): Boolean {
        require(index < size)
        if (array[index].compareAndSet(expect, element)) {
            _size.compareAndSet(index, index + 1)
            return true
        }
        return false
    }

    fun add(index: Int, element: E?): Boolean {
        if (index >= array.size) {
            return false
        }
        require(index <= size)
        if (array[index].compareAndSet(null, element)) {
            _size.compareAndSet(index, index + 1)
            return true
        }
        _size.compareAndSet(index, index + 1)
        return false
    }
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME