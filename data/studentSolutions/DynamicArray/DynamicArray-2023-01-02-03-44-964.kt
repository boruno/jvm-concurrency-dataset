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
//    private val core = atomic(Core<E>(INITIAL_CAPACITY))
    private val core = atomic(Core<Element<E>>(INITIAL_CAPACITY))
    private val _size = atomic(0)

    @Suppress("UNCHECKED_CAST")
    override fun get(index: Int): E {
        if (index >= size) {
            throw IllegalArgumentException()
        }

        return core.value.get(index).element.value as E
    }

    override fun put(index: Int, element: E) {
        if (index >= size) {
            throw IllegalArgumentException()
        }

        while (true) {
            val e = core.value.get(index)
            if (e.isBeingUsedByPut.compareAndSet(false, true)) {
                if (e.isBeingUsedByPush.value == false) {
                    e.element.value = element
                    e.isBeingUsedByPut.compareAndSet(true, false)
                    return
                }
                e.isBeingUsedByPut.compareAndSet(true, false)
            }
        }
    }

    override fun pushBack(element: E) {
        while (true) {
            val current_core = core

            if (size == current_core.value.getCapacity()) {
                if (current_core.value.isBeingMoved.compareAndSet(false, true)) {
                    var new_core = Core<Element<E>>(current_core.value.getCapacity() * 2)
                    for (i in 0 until size) {
                        current_core.value.get(0).isBeingUsedByPush.compareAndSet(false, true)
                        while (current_core.value.get(0).isBeingUsedByPut.value) {}
                        new_core.get(0).element.value = element
                    }
                    new_core.get(size).element.value = element
                    core.compareAndSet(core.value, new_core)
                    _size.compareAndSet(_size.value, _size.value + 1)
                    return
                }
                else {
                    continue
                }
            }
            else {
                if (current_core.value.get(size).isBeingUsedByPush.compareAndSet(false, true)) {
                    current_core.value.get(size).element.value = element
                    current_core.value.get(size).isBeingUsedByPush.compareAndSet(true, false)
                    _size.compareAndSet(_size.value, _size.value + 1)
                    return
                }
                else {
                    continue
                }
            }
        }
    }

//    override val size: Int get() = core.value.size
    override val size: Int get() = _size.value
}

private class Element<E>(element: E? = null) {
    val isBeingUsedByPut = atomic(false)
    val isBeingUsedByPush = atomic(false)
    val element = atomic(element)
}

private class Core<E>(
    capacity: Int,
) {
    private val array = atomicArrayOfNulls<E>(capacity)
    val isBeingMoved = atomic(false)
//    private val _size = atomic(0)
//
//    val size: Int = _size.value
//    val capacity: Int = array.size

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E {
//        require(index < size)
        require(index < array.size)
        return array[index].value as E
    }

    fun getCapacity(): Int {
        return array.size
    }
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME