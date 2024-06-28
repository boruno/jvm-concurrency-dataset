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
    private val core = atomic(Pair<Core, Core?>(Core(INITIAL_CAPACITY), null))

    private fun helpExtendSize(oldPair: Pair<Core, Core?>, core1: Core, core2: Core) {
        for (i in (0 until core1.capacity)) {
            // core2.array[i].compareAndSet(null, core1.array[i].value)
            while (true) {
                val x = core1.array[i].value
                if (x is Moving) {
                    core2.array[i].compareAndSet(null, x.element)
                    break
                }
                if (core1.array[i].compareAndSet(x, Moving(x))) {
                    core2.array[i].compareAndSet(null, x)
                }
            }
        }
        core.compareAndSet(oldPair, Pair(core2, null))
    }

    override fun get(index: Int): E {
        val x = core.value
        val res = x.first.array[index].value
        if (res is Moving) { return res.element as E }
        return res as E
    }

    override fun put(index: Int, element: E) {
        assert(element != null)
        outer@while (true) {
            val x = core.value
            val s = x.second
            if (s != null) {
                helpExtendSize(x, x.first, s)
                continue
            }
            require(x.first.size.value >= index)
            while (true) {
                val old = x.first.array[index].value
                if (old is Moving) {
                    continue@outer
                }
                if (x.first.array[index].compareAndSet(old, element)) { return }
            }
        }
    }

    override fun pushBack(element: E) {
        while (true) {
            val x = core.value
            val s = x.second
            if (s != null) {
                helpExtendSize(x, x.first, s)
                continue
            }
            val index = x.first.size.getAndIncrement()
            if (index < x.first.capacity) {
                x.first.array[index].value = element
                return
            }
            val newCore = Core(x.first.capacity + 1)
            newCore.size.value = x.first.capacity + 1
            newCore.array[x.first.capacity].value = element
            if (!core.compareAndSet(x, Pair(x.first, newCore))) { continue }
            helpExtendSize(x, x.first, newCore)
            return
        }
    }

    override val size: Int get() = core.value.first.size.value
}

private class Core(
    val capacity: Int,
) {
    val array = atomicArrayOfNulls<Any>(capacity)
    val size = atomic(0)
}

private class Moving(val element: Any?) {
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME