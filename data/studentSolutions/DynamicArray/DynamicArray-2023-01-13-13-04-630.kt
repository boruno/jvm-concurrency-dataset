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
    private val sz = atomic(0)
    override fun get(index: Int): E {
        require(index < core.value.size)

            var e = core.value.get(index) ?: throw RuntimeException("unexpected case")
            return e.first
    }

    override fun put(index: Int, element: E) {
        require(index < size)
        var e = core.value.array[index].getAndSet(Pair(element, false))
        while (true) {
            val nextNode = core.value.next.value
            when {
                nextNode != null -> {
                    val y = core.value.array[index].value
                    if (y != null) {
                        nextNode.array[index].getAndSet(y)
                    }
                    core.value = nextNode
                }
                else -> return
            }
        }
        //TODO("Not yet implemented")
    }

    override fun pushBack(element: E) {
        while (true) {
            val curSize = sz.value
            val curCore = core.value
            if (curSize < curCore.size) {
                if (curCore.array[curSize].compareAndSet(null, Pair(element, false))) {
                    sz.compareAndSet(curSize, curSize + 1)
                    return
                } else {
                    sz.compareAndSet(curSize, curSize + 1)
                }
            } else  {
                var newCore = Core<E>(2 * curCore.size)
                if (curCore.next.compareAndSet(null, newCore)) {
                    for (i in 0 until curCore.size) {
                        val elem = curCore.array[i].value
                        if (elem != null) {
                            curCore.array[i].compareAndSet(null, elem)
                        }
                    }
                    core.compareAndSet(curCore, newCore)
                } else {
                    var nextNode = curCore.next.value
                    if (nextNode != null) {
                        for (i in 0 until curCore.size) {
                            val elem = curCore.array[i].value
                            if (elem != null) {
                                curCore.array[i].compareAndSet(null, elem)
                            }
                        }
                        core.compareAndSet(curCore, newCore)
                    }
                }
//                curCore.next.compareAndSet(null, Core(2 * curCore.size))
//                for (i in 0 until curSize) {
//                    while (true) {
//                        val elem = curCore.array[i].value ?: continue
//                        val frozenElem = Pair(elem.first, true)
//                        if (curCore.array[i].compareAndSet(elem, frozenElem)) {
//                            newCore.array[i].value = elem
//                            break
//                        }
//                    }
//                }
//                core.value = newCore
            }
        }
    }

    override val size: Int get() = core.value.size
}

 private class Core<E>(
    capacity: Int,
) {
     val next: AtomicRef<Core<E>?> = atomic(null)
     val array = atomicArrayOfNulls<Pair<E, Boolean>>(capacity)
    private val _size = atomic(0)

    val size: Int = _size.value

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): Pair<E, Boolean>? {
        require(index < size)
        return array[index].value
    }
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME