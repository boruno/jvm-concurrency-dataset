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

class DynamicArrayImpl<E> : DynamicArray<E> {
    private val core = atomic(Core<E>(INITIAL_CAPACITY))
    private val array = atomic(0)

    @Suppress("UNCHECKED_CAST")
    override fun get(index: Int): E {
        require(index < size)
        while (true) {
            val core = this.core.value
            if (index < core.capacity) {
                return core.array[index].value ?: continue
            }
        }
    }

    override fun put(index: Int, element: E) {
        require(index < size)
        while (true) {
            val core = this.core.value
            if (index < core.capacity) {
                val a = core.array[index].value ?: continue
                if (core.array[index].compareAndSet(a, element)) {
                    if (a == core.array[index].value) return
                }
            }
        }
    }

    override fun pushBack(element: E) {
        val index = array.getAndIncrement()
        while (true) {
            val core = this.core.value
            if (index >= core.capacity) {
                resize(core)
                continue
            }
            if (core.array[index].compareAndSet(null, element)) {
                return
            }
        }
    }

    fun resize(core: Core<E>) {
        val next = Core<E>(2 * core.capacity)
        if (core.next.compareAndSet(null, next)) {
            for (i in 0 until core.capacity) {
                while (true) {
                    val a = core.array[i].getAndSet(null)
                    if(a != null) {
                        next.array[i].getAndSet(a)
                        break
                    }
                }
            }
            this.core.compareAndSet(core, next)
        }
    }

    override val size: Int get() = array.value
}

class Core<E>(
    val capacity: Int,
) {
    val array = atomicArrayOfNulls<E>(capacity)
    val next: AtomicRef<Core<E>?> = atomic(null)
    private val _size = atomic(0)
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME