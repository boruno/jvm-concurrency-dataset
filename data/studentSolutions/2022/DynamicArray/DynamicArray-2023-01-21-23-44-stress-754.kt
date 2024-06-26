package mpp.dynamicarray

import kotlinx.atomicfu.*
import java.util.stream.IntStream.range

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


    private fun move(curCore: Core<E>) {
        if (curCore.next.value === null) {
            val newCore = Core<E>(2 * curCore.getSize(), curCore.getSize())
            curCore.next.compareAndSet(null, newCore)
        }
        helper()
    }

    private fun helper() {
        val curCore = core.value
        val nextCore = curCore.next.value
        if (nextCore === null) {
            return
        }
        for (i in range(0, curCore.getArraySize())) {
            val elementOld = curCore.get(i)

            if (elementOld === null) {
                curCore.put(i, null, Moved())
            }

            if (elementOld is Value) {
                val element: E = elementOld.element
                if (curCore.put(i, elementOld, InProcess(element))) {
                    (nextCore.put(i, null, elementOld))
                }

            }

            if (elementOld is Moved) {
            }

            if (elementOld is InProcess) {
                (nextCore.put(i, null, elementOld))
            }
        }

        core.compareAndSet(curCore, nextCore)
    }


    override fun get(index: Int): E {
        require(index < size)
        while (true) {
            var curCore = core.value
            val currentValue = curCore.get(index)
            if (currentValue is Value) {
                return currentValue.element
            }
            if (currentValue is InProcess) {
                return currentValue.element
                helper()
            }
            helper()
        }
    }

    override fun put(index: Int, element: E) {
        require(index < size)
        while (true) {
            var curCore = core.value
            val currentValue = curCore.get(index)
            if (currentValue is Value) {
                if (curCore.put(index, currentValue, Value(element))) {
                    return
                }
            }
            helper()
        }
    }

    override fun pushBack(element: E) {
        while (true) {
            val curCore = core.value
            val curSize = curCore.getSize()
            if (curSize < curCore.getArraySize()) {
                if (curCore.put(curSize, null, Value(element))) {
                    curCore.inc(curSize)
                    return
                }
                curCore.inc(curSize)
                helper()
            } else {
                move(curCore)
            }
        }
    }

    override val size: Int get() = core.value.getSize()
}

private interface Wrapper<E>

private class InProcess<E>(val element: E) : Wrapper<E> //V' на лекции
private class Moved<E>() : Wrapper<E> // S
private class Value<E>(val element: E) : Wrapper<E> //V

private class Core<E>(
    capacity: Int, curSize: Int = 0
) {
    private val array = atomicArrayOfNulls<Wrapper<E>>(capacity)
    private val _size = atomic(curSize)
    val next = atomic<Core<E>?>(null)

    fun getSize() = _size.value

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): Wrapper<E>? {
        return array[index].value
    }

    fun put(index: Int, prevElement: Wrapper<E>?, element: Wrapper<E>?): Boolean {
        return (array[index].compareAndSet(prevElement, element))
    }

    fun inc(prevSize: Int): Boolean {
        return _size.compareAndSet(prevSize, prevSize + 1)
    }

    fun getArraySize(): Int {
        return array.size
    }

}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME