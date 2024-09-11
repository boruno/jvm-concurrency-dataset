package mpp.dynamicarray

import kotlinx.atomicfu.*
import java.util.concurrent.atomic.AtomicReference
import javax.lang.model.element.Element

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

    override fun get(index: Int): E {
        if (index >= size) {
            throw IllegalArgumentException()
        }

        while (true) {
            val cur_core = core.value
            val cur_element = cur_core.get(index)
            val destination = cur_core.next.get()
            if (destination == null && cur_element != null) return cur_element
        }
    }

    override fun put(index: Int, element: E) {
        if (index >= size) {
            throw IllegalArgumentException()
        }

        while (true) {
            val cur_core = core.value
            val destination = cur_core.next.get()
            if (cur_core.CAS(index, cur_core.get(index), element)) {
                if (destination == null) {
                    return
                }
                else {
                    destination.CAS(index, destination.get(index), element)
                    if (destination.next.get() == null) return
                }
            }
        }
    }

    override fun pushBack(element: E) {
        while (true) {
            val cur_core = core.value
            val cur_size = cur_core.size.value

            if (cur_size < cur_core.getCapacity()) {
                if (cur_core.CAS(cur_size, null, element)) {
                    cur_core.size.compareAndSet(cur_size, cur_size + 1)
                    return
                }
                else {
                    cur_core.size.compareAndSet(cur_size, cur_size + 1)
                }
            }
            else {
                val new_core = Core<E>(cur_core.getCapacity() * 2)
                new_core.size.compareAndSet(0, cur_size)
                new_core.CAS(cur_size, null, element)

                if (cur_core.next.compareAndSet(null, new_core)) {
                    for (i in 0 until cur_size) {
                        new_core.CAS(i, null, cur_core.get(i))
                    }
                    new_core.size.compareAndSet(cur_size, cur_size + 1)
                    core.compareAndSet(cur_core, new_core)
                    return
                }
                else { // help
                    val destination = cur_core.next.get()!!
                    for (i in 0 until cur_size) {
                        new_core.CAS(i, null, cur_core.get(i))
                    }
                    destination.size.compareAndSet(cur_size, cur_size + 1)
                    core.compareAndSet(cur_core, destination)
                }
            }
        }
    }

    override val size: Int get() = core.value.size.value
}

//private class Element<E>(element: E? = null) {
//    val movedTo = AtomicReference<Core<E>?>(null) // используется ли сейчас данный элемент пушем или путом
//    val element = atomic(element) // хранит значение элемента, если элемент был перемещен пушем то устанавливается нулл
//}

private class Core<E>(
    capacity: Int,
) {
    private val array = atomicArrayOfNulls<E?>(capacity)
    val size = atomic(0)
    val next = AtomicReference<Core<E>?>(null)

    fun get(index: Int): E? {
        require(index < size.value)
        return array[index].value
    }

    fun CAS(index: Int, expected: E?, update: E?): Boolean {
        return array[index].compareAndSet(expected, update)
    }

    fun getCapacity(): Int {
        return array.size
    }
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME