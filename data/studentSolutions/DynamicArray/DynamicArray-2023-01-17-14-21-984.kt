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

/*class DynamicArrayImpl<E> : DynamicArray<E> {
    private val core = atomic(Core<E>(INITIAL_CAPACITY))

    override fun get(index: Int): E = core.value.get(index)

    override fun put(index: Int, element: E) {
        require(index < size)
        while (true) {
            val last = core.value.array[index].value

            if (!last!!.removed && core.value.array[index].compareAndSet(last, Elem(element, false)))
                return
        }
    }

    override fun pushBack(element: E) {

    }

    override val size: Int get() = core.value.size

    private fun ensureCapacity(current: Core<E>) {

    }

    private class Core<E>(capacity: Int) {
        val array = atomicArrayOfNulls<Elem<E>>(capacity)
        private val _size = atomic(0)
        val next: AtomicRef<Core<E>?> = atomic(null)
        val size: Int = _size.value

        @Suppress("UNCHECKED_CAST")
        fun get(index: Int): E {
            require(index < size)
            return array[index].value!!.value
        }
    }

    private class Elem<E>(val value: E, val removed: Boolean)
}*/

class DynamicArrayImpl<E> : DynamicArray<E> {
    private val core = atomic(Core<E>(INITIAL_CAPACITY))

    override fun get(index: Int): E {
        val indexCore = findIndexCore(core.value, index)
        val wrapper = findNotMovedValue(indexCore, index).second

        return wrapper.element
    }

    private fun findIndexCore(currentCore: Core<E>, index: Int): Core<E> {
        var curCore = currentCore

        while (index >= curCore.array.size) {
            if (curCore.next.value == null) throw IllegalArgumentException()

            curCore = curCore.next.value!!
        }

        return curCore
    }

    private fun findNotMovedValue(currentCore: Core<E>, index: Int): Pair<Core<E>, Wrapper<E>> {
        var curCore = currentCore
        var wrapper = curCore.array[index].value

        while (wrapper is Moved) {
            curCore = curCore.next.value!!
            wrapper = curCore.array[index].value
        }

        return Pair(curCore, wrapper ?: throw IllegalArgumentException())
    }

    override fun put(index: Int, element: E) {
        var curCore = findIndexCore(core.value, index)

        while (true) {
            val pair = findNotMovedValue(curCore, index)
            val wrapper = pair.second
            curCore = pair.first

            when (wrapper) {
                is Element -> {
                    if (curCore.transferInProgress.value) {
                        if (!curCore.array[index].compareAndSet(wrapper, Frozen(element))) continue

                        if (!transfer(curCore, index, wrapper, element)) continue
                    } else {
                        if (!curCore.array[index].compareAndSet(wrapper, Element(element))) continue
                    }
                }
                is Frozen -> {
                    if (!transfer(curCore, index, wrapper, element)) continue
                }
            }
            return
        }
    }

    private fun transfer(curCore: Core<E>, index: Int, frozenElement: Wrapper<E>, element: E): Boolean {
        val next = curCore.next.value!!

        if (next.array[index].value != null) curCore.array[index].compareAndSet(frozenElement, Moved(element))

        if (!next.array[index].compareAndSet(null, Element(element))) return false

        if (curCore.transferredSize.incrementAndGet() == curCore.array.size) {
            core.compareAndSet(curCore, curCore.next.value!!)
        }

        return true
    }


    override fun pushBack(element: E) {
        while (true) {
            var curCore = core.value
            var pushIndex = curCore.pushIndex.value

            while (pushIndex == curCore.array.size) {
                curCore = curCore.next.value!!
                pushIndex = curCore.pushIndex.value
            }

            if (!curCore.array[pushIndex].compareAndSet(null, Element(element))) continue


            if (++pushIndex == curCore.array.size) {
                curCore.next.update { Core(curCore.array.size * 2, curCore.array.size) }
                curCore.transferInProgress.compareAndSet(false, update = true)
            }

            curCore.pushIndex.incrementAndGet()

            return
        }
    }

    override val size: Int get() : Int {
        var curCore = core.value

        while (curCore.next.value != null) curCore = curCore.next.value!!

        var pushIndex = curCore.pushIndex.value

        while (pushIndex < curCore.array.size && curCore.array[pushIndex].value != null) { pushIndex++ }

        return pushIndex
    }

}

private class Core<E>(capacity: Int, prevCapacity: Int = 0) {
    val pushIndex = atomic(prevCapacity)
    val transferredSize = atomic(0)
    val transferInProgress = atomic(false)
    val array = atomicArrayOfNulls<Wrapper<E>>(capacity)
    val next = atomic<Core<E>?>(null)
}

private interface Wrapper<E> {
    val element: E
}

private class Frozen<E>(override val element: E) : Wrapper<E>
private class Moved<E>(override val element: E) : Wrapper<E>
private class Element<E>(override val element: E) : Wrapper<E>


private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME