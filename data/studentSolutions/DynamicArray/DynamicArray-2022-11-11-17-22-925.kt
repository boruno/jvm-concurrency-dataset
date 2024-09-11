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
    private val core = atomic(Core<E>(INITIAL_CAPACITY))

    override fun get(index: Int): E = core.value.get(index)
    override val size: Int
        get() = size()

    override fun put(index: Int, element: E) {
        require(index < size)
        core.value.put(index, element)
    }

    override fun pushBack(element: E) {
        var c = core.value
        while (!c.insertToEnd(element)) {
            val _c = core.value.resize()
            if (!core.compareAndSet(c, _c)) {
                //println("someone was faster than me")
            }
            c = core.value

        }
    }

    fun size(): Int {
        var c = core.value
        while (c.next.value != null) {
            c = c.next.value!!
        }
        return c.size
    }
}

private class Core<E>(
    val capacity: Int,
) {
    private val array = atomicArrayOfNulls<E>(capacity)
    private val moved = atomicArrayOfNulls<Boolean?>(capacity)
    private val _size = atomic(0)

    val size: Int get() = _size.value
    val lastSizeToPushHere = atomic<Int?>(null)

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E {
        require(index < size)
        if (moved[index].value != true) {
            return array[index].value as E
        } else {
            assert(next.value != null)
            return next.value!!.array[index].value as E
        }
    }

    fun put(index: Int, value: E) {
        require(index < capacity)
        while (true) {
            val v = array[index].value
            if (moved[index].value == null) {
                if (!array[index].compareAndSet(v, value)) {
                    continue
                }
                if (moved[index].value != null) {
                    continue
                }
                break
            } else {
                assert(next.value != null)
                next.value!!.put(index, value)
                break
            }
        }
    }

    fun insertToEnd(value: E): Boolean {
        var s: Int

        do {
            s = size
            if (s >= capacity) {
                return false
            }
            if (next.value != null) {
                return false
            }
            if (lastSizeToPushHere.value != null && lastSizeToPushHere.value!! < s) {
                return false
            }
        } while (!_size.compareAndSet(s, s + 1))

        if (!array[s].compareAndSet(null, value)) {
            print("oh crap")
        }
        return true
    }

    // --------------

    val next = atomic<Core<E>?>(null)

    fun resize(): Core<E> {
        val s = _size.value
        val c = Core<E>(capacity * 2)
        c._size.compareAndSet(0, s)
        if (next.compareAndSet(null, c)) {
            _size.value = Int.MAX_VALUE
            lastSizeToPushHere.compareAndSet(null, s)
            for (i in 0 until _size.value) {
                // mark as not available to write
                // since here readers can read from this, no write
                moved[i].compareAndSet(null, false)
                val v = array[i].value
                // copy value knowing nobody gonna change it here
                // p.s. put
                c.array[i].compareAndSet(null, v)
                // mark this element  as migrated
                // readers must read from next and write to next
                moved[i].compareAndSet(false, true)
            }
        }
        return next.value!!
    }
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME