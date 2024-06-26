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
    private val _size = atomic(0)

    override fun get(index: Int): E {
        if (index >= size) {
            throw IllegalArgumentException()
        }

        while (true) {
            val result = core.value.get(index)
            if (result != null) {
                if (result.second == null) {
                    return result.first
                }
            }
        }
    }

    override fun put(index: Int, element: E) {
        if (index >= size) {
            throw IllegalArgumentException()
        }

        while (true) {
            val cur_element = core.value.get(index) ?: continue
            if (cur_element.second == null) {
                if (core.value.CAS(index, cur_element, Pair(element, null))) {
                    return
                }
            }
            else { // help
                if (cur_element.second!!.CAS(index, null, Pair(element, null))) {
                    return
                }
            }
        }
    }

    override fun pushBack(element: E) {
        while (true) {
            val cur_size = _size.value
            val cur_core = core.value

            if (cur_size < cur_core.getCapacity()) {
                if (cur_core.CAS(cur_size, null, Pair(element, null))) {
                    _size.compareAndSet(cur_size, cur_size + 1)
                    return
                }
                else {
                    _size.compareAndSet(cur_size, cur_size + 1)
                }
            }
            else {
                val new_core = Core<E>(cur_size * 2)
                if (core.compareAndSet(cur_core, new_core)) {
                    for (i in 0 until cur_size) {
                        var cur_element = cur_core.get(i)
                        while (true) {
                            if (cur_element != null) {
                                if (cur_core.CAS(i, cur_element, Pair(cur_element.first, new_core))) {
                                    break
                                }
                            }
                            cur_element = cur_core.get(i)
                        }
                        new_core.CAS(i, null, cur_element)
                    }
                    new_core.CAS(cur_size, null, Pair(element, null))
                    _size.getAndIncrement()
                    return
                }
                else { // help
                    for (i in 0 until cur_size) {
                        val cur_element = cur_core.get(i)
                        if (cur_element != null) {
                            if (cur_element.second != null) {
                                cur_element.second!!.CAS(i, null, Pair(cur_element.first, null))
                            }
                        }
                    }
                }
            }
        }
    }

    override val size: Int get() = _size.value
}

//private class Element<E>(element: E? = null) {
//    val movedTo = AtomicReference<Core<E>?>(null) // используется ли сейчас данный элемент пушем или путом
//    val element = atomic(element) // хранит значение элемента, если элемент был перемещен пушем то устанавливается нулл
//}

private class Core<E>(
    capacity: Int,
) {
    private val array = atomicArrayOfNulls<Pair<E, Core<E>?>>(capacity)

//    init {
//        for (i in 0 until capacity) {
//            array[i].value = Element()
//        }
//    }

    fun get(index: Int): Pair<E, Core<E>?>? {
        require(index < array.size)
        return array[index].value
    }

    fun CAS(index: Int, expected: Pair<E, Core<E>?>?, update: Pair<E, Core<E>?>?): Boolean {
        return array[index].compareAndSet(expected, update)
    }

    fun getCapacity(): Int {
        return array.size
    }
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME