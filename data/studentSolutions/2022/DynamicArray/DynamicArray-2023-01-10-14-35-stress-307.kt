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
        while (true) {
            val oldCore = core.value
            val pos = oldCore.sz()
            if (oldCore.check()) {
                resize()
            } else {
                if (oldCore.cas(element, pos)) {
                    oldCore.incr()
                    return
                }
            }
        }

    }


    override val size: Int get() = core.value.size


    private fun resize() {
        if (flag.compareAndSet(false, true)) {
            val oldCore = core.value
            val sz = oldCore.sz()
            if (oldCore.check()) {
                val newCore = Core<E>(sz * 2);
                newCore.changeSZ(sz)
                for (it in 0 until sz) {
                    newCore.putt(it, oldCore.get(it))
                }
                core.getAndSet(newCore);
            }
            flag.getAndSet(true);
        }
    }

}

private class Core<E>(
    capacity: Int,
) {
    val array = atomicArrayOfNulls<E>(capacity)
    val _size = atomic(0)

    val size: Int
        get() = _size.value

    fun check() : Boolean {
        return _size.value >= array.size
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

    fun putt(index: Int, x: E) {
        //require(index <= array.size)
        array[index].getAndSet(x)
    }

    fun changeSZ(x: Int) {
        _size.getAndSet(x)
    }

    fun incr() : Int {
        return _size.getAndIncrement();
    }

    fun sz() : Int {
        return _size.value
    }

    fun cas(el: E, ind: Int) : Boolean {
        return array[ind].compareAndSet(null, el)
    }
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME


