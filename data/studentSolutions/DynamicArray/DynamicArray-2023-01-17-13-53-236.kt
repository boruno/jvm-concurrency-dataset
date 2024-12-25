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
    private val len = atomic(0)

    override fun get(index: Int): E {
        require(index < len.value) { "Index out of bounds" }
        val core = this.core.value
        return core.array[index].value!!
    }

    override fun put(index: Int, element: E) {
        require(index < len.value) { "Index out of bounds" }
        var core = this.core.value
        core.array[index].value = element
        while (true) {
            val next = core.next.value ?: return
            val v = core.array[index].value
            if (v != null) {
                next.array[index].value = v
            }
            core = next
        }
    }

    override fun pushBack(element: E) {
        while (true) {
            val core = this.core.value
            val size = len.value
            if (size < core.capacity) {
                if (core.array[size].compareAndSet(null, element)) {
                    len.getAndIncrement()
                    return
                }
                continue
            }

            // rehash
            core.next.compareAndSet(null, Core(core.capacity * 2))
            val next = core.next.value!!
            repeat(core.capacity) { i ->
                val oldVal = core.array[i].value ?: return@repeat
                next.array[i].compareAndSet(null, oldVal)
            }
            this.core.compareAndSet(core, next)
        }
    }

    override val size: Int
        get() {
            return len.value
        }
}

private class Core<E>(val capacity: Int) {
    val array = atomicArrayOfNulls<E>(capacity)
    val next: AtomicRef<Core<E>?> = atomic(null)
}


private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME