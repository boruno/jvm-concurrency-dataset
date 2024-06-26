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
    private val core = atomic(Core<E>(INITIAL_CAPACITY))
    private val flag = atomic(false)

    override fun get(index: Int): E = core.value.get(index)

    override fun put(index: Int, element: E) = core.value.put(index, element)

    override fun pushBack(element: E) {
        val oldCore = core.value
        val pos = oldCore.incr()
        while (true) {
            if (oldCore.check()) {
                resize()
            } else {
                oldCore.put(pos, element)
                return
            }
        }
    }

    override val size: Int get() = core.value.size

    fun resize() {
        val oldCore = core.value
        while (!flag.compareAndSet(false, true)) {

        }
        val sz = oldCore.size
        val newCore = Core<E>(sz * 2);
        newCore.changeSZ(sz)
        for (it in 0 until sz) {
            newCore.put(it, oldCore.get(it))
        }
        core.getAndSet(newCore);
        flag.getAndSet(true);
    }
}

private class Core<E>(
    capacity: Int,
) {
    private val array = atomicArrayOfNulls<E>(capacity)
    private val _size = atomic(0)

    val size: Int = _size.value

    fun check() : Boolean {
        return size >= array.size
    }

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E {
        require(index < size)
        return array[index].value as E
    }

    fun put(index: Int, x: E) {
        require(index < size)
        array[index].getAndSet(x)
    }

    fun changeSZ(x: Int) {
        _size.getAndSet(x)
    }

    fun incr() : Int {
        return _size.getAndIncrement();
    }
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME
