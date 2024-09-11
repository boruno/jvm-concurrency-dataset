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
    private val core = atomic(Core<E>(INITIAL_CAPACITY, null))

    override fun get(index: Int): E = core.value.get(index)

    override fun put(index: Int, element: E) {
        require(index < size)
        while (true) {
            val cor = core.value;
            cor.array[index].getAndSet(element)
            if (core.compareAndSet(cor, cor)) {
                return
            }
        }
    }

    override fun pushBack(element: E) {
        while (true) {
            val cor = core.value;
            val sz = cor._size.value;
            println("##" + sz)
            var curcor = cor;
            if (sz == cor.array.size) {
                val newcore = Core<E>(cor.array.size * 2, cor)
                if (!core.compareAndSet(cor, newcore)) {
                    continue
                }
                for (i in 0 until cor.array.size) {
                    newcore.array[i].compareAndSet(null, cor.array[i].value)
                }
                curcor = newcore;
            }
            if (curcor._size.compareAndSet(sz, sz + 1)) {
                println(">>" + sz)
                //println("yeah pushing")
                put(sz, element)
                //println("successfully pushed")
                return
            }
        }
    }

    override val size: Int get() {return core.value._size.value}
}

private class Core<E>(
    capacity: Int,
    val oldCore: Core<E>?
) {
    val array = atomicArrayOfNulls<E>(capacity)
    val _size = atomic(0)

    val size: Int get() = _size.value

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E {
        require(index < size)
        if (array[index].value == null) {
            array[index].compareAndSet(null, oldCore!!.get(index))
        }
        return array[index].value as E
    }
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME