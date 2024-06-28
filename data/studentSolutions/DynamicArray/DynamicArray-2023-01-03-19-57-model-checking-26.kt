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
    private val _size = atomic(0)

    override fun get(index: Int): E {
        if (index >= size) {
            throw IllegalArgumentException()
        }

        while (true) {
            val current_core = core.value
            val element = current_core.get(index)
            if (element != null) {
                return element
            }
        }
    }

    override fun put(index: Int, element: E) {
        if (index >= size) {
            throw IllegalArgumentException()
        }

        while (true) {
            val current_core = core // текущее ядро
            val current_core_element = current_core.value.get(index)
            if (current_core_element != null) {
                if (current_core.value.CAS(index, current_core_element, element)) {
                    return
                }
            }
        }
    }

    override fun pushBack(element: E) {
        while (true) {
            val current_core = core.value // запомнили текущее ядро
            val current_size = size // запомнили текущий размер

            if (current_size < current_core.getCapacity()) {
                if (current_core.CAS(current_size, null, element)) {
                    if (_size.compareAndSet(current_size, current_size + 1)) {
                        return
                    }
                    else {
                        current_core.CAS(current_size, element, null)
                        continue
                    }
                }
            }
            else if (current_size == current_core.getCapacity()) {
                var current_core_first_element = current_core.get(0)

                while (current_core_first_element != null) {
                    if (current_core.CAS(0, current_core_first_element, null)) {
                        val new_core = Core<E>(current_core.getCapacity() * 2)
                        new_core.CAS(0, null, current_core_first_element)
                        if (current_size > 1) {
                            for (i in 1 until current_size) {
                                var current_core_element = current_core.get(i)
                                while (!current_core.CAS(i, current_core_element, null)) {
                                    current_core_element = current_core.get(i)
                                }
                                new_core.CAS(i, null, current_core_element)
                            }
                        }
                        _size.compareAndSet(current_size, current_size + 1)
                        core.compareAndSet(current_core, new_core)
                        return
                    }
                    current_core_first_element = current_core.get(0)
                }
            }
        }
    }

    override val size: Int get() = _size.value
}

//private class Element<E>(element: E? = null) {
//    val isBeingUsed = atomic(false) // используется ли сейчас данный элемент пушем или путом
//    val element = atomic(element) // хранит значение элемента, если элемент был перемещен пушем то устанавливается нулл
//}

private class Core<E>(
    capacity: Int,
) {
    private val array = atomicArrayOfNulls<E>(capacity)

    fun get(index: Int): E? {
        require(index < array.size)
        return array[index].value
    }

    fun CAS(index: Int, expected: E?, update: E?): Boolean{
        return array[index].compareAndSet(expected, update)
    }

    fun getCapacity(): Int {
        return array.size
    }
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME