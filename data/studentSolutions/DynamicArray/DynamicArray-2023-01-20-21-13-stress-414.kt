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

    override fun get(index: Int): E = core.value.get(index)

    override fun put(index: Int, element: E) {
        var head = core.value
        if (index < head._size.value) {
            head.array[index].getAndSet(element)
            while (true) {
                if (head.get(index) == null || head.nxt.value == null) {
                    return
                }
                head.nxt.value!!.array[index].getAndSet(head.get(index))
                head = head.nxt.value!!
            }
        }
        throw IllegalArgumentException()
    }

    override fun pushBack(element: E) {
        while (true) {
            val head = core.value
            val size = head._size.value
            if (size >= head.capacity) {
                val tmp = Core<E>(2 * head.capacity)
                tmp._size.getAndSet(head.capacity)
                if (head.nxt.compareAndSet(null, tmp)) {
                    for (i in 0 until head.capacity) {
                        tmp.array[i].compareAndSet(null, head.get(i))
                    }
                    core.compareAndSet(head, tmp)
                } else {
                    if (head.nxt.value == null) continue
                    for (i in 0 until head.capacity) {
                        head.nxt.value!!.array[i].compareAndSet(null, head.get(i))
                    }
                    core.compareAndSet(head, head.nxt.value!!)
                }
            } else {
                if (!head.array[size].compareAndSet(null, element)) {
                    head._size.compareAndSet(size, size + 1)
                    break
                }
                head._size.compareAndSet(size, size + 1)
            }
        }
    }

    override val size: Int get() = core.value._size.value
}

private class Core<E>(
    nxt: Core<E>?,
    var capacity: Int
) {
    constructor(capacity: Int) : this(null, capacity)

    val array = atomicArrayOfNulls<E>(capacity)
    val _size = atomic(0)
    val size: Int = _size.value
    val nxt = atomic(nxt)

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E {
        require(index < _size.value)
        return array[index].value as E
    }
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME