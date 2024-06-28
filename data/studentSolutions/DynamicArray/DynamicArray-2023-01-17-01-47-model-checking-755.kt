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
    private val aSize = atomic(0)

    override fun get(index: Int): E {
        if (index >= size || index < 0) {
            throw IllegalArgumentException()
        }

        while (true) {
            val core = this.core.value
            if (index > core.capacity - 1) continue

            val wrapper = core.array[index].value ?: continue
            return wrapper.element.value ?: continue
        }
//        return core.value.array[index].value!!.element.value!!
    }

    override fun put(index: Int, element: E) {
        if (index >= size || index < 0) {
            throw IllegalArgumentException()
        }

        while (true) {
            val core = this.core.value
            if (core.capacity <= index) continue

            val wrapper = core.array[index].value ?: continue

            if (wrapper.element.compareAndSet(wrapper.element.value, element) && core.array[index].value == wrapper) {
                return
            }
        }
    }

    override fun pushBack(element: E) {
        val sizeIncremented = aSize.getAndIncrement()
        while (true) {
            val core = this.core.value
            if (sizeIncremented > core.capacity - 1) {
                val newCore = Core<E>(2 * core.capacity)
                if (core.next.compareAndSet(null, newCore)) {
                    for (i in 0 until core.capacity) {
                        while (true) {
                            val value = core.array[i].getAndSet(null)
                            if (value != null) {
                                newCore.array[i].compareAndSet(null, value)
                                break
                            }
                        }
                    }
                    this.core.compareAndSet(core, newCore)
                }
            } else if (core.array[sizeIncremented].compareAndSet(null, CoreWrapper(atomic(element)))) {
                break
            }
        }
    }

    override val size: Int
        get() {
            return aSize.value
        }
}

private class CoreWrapper<T>(val element: AtomicRef<T>)

private class Core<E>(val capacity: Int) {
    val array = atomicArrayOfNulls<CoreWrapper<E>>(capacity)
    val next: AtomicRef<Core<E>?> = atomic(null)
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME