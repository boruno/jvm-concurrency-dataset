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


    private fun move(curCore: Core<E>){
        if (curCore.next.value === null){
            val newCore = Core<E>(2 * curCore.size)
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
        for (i in range(0, curCore.size)) {
            val elementOld = curCore.get(i)
            val elementNew = nextCore.get(i)

            if (elementNew !== null) {
                continue
            }

            if (elementOld is Value) {
                val element: E = elementOld.element
                if (curCore.put(i, InProcess(element), elementOld)) {
                    if (nextCore.put(i, null, elementOld)) {
                        curCore.put(i, InProcess(element), Moved(element))
                    }
                }

            }

            if (elementOld is Moved) {}

            if (elementOld is InProcess) {
                if (nextCore.put(i, null, elementOld)) {
                    val element: E = elementOld.element
                    curCore.put(i, InProcess(element), Moved(element))
                }
            }
        }

        core.compareAndSet(curCore, nextCore)

//        core.compareAndSet(curCore, nextCore)
    }


    override fun get(index: Int): E {
        var curCore = core.value
        while (true) {
            val currentValue = curCore.get(index)
            if (currentValue is Value) {
                return currentValue.element
            }
            if (currentValue is InProcess) {
                return currentValue.element
            }
            if (currentValue is Moved) {
                curCore = curCore.next.value!!
                helper()
            }
        }

    }

    override fun put(index: Int, element: E) {
        var curCore = core.value
        while (true) {
            val currentValue = curCore.get(index)
            if (currentValue is Value) {
                if (core.value.put(index, Value(element), currentValue)) {
                    return
                }
                continue
            }
            if (currentValue is InProcess || currentValue is Moved) {
                helper()
            }
        }
    }

    override fun pushBack(element: E) {
        while (true) {
            val curCore = core.value
            val nextCore = curCore.next.value
            val curSize = curCore.size
            if (curSize + 1 <= curCore.array.size){
                if (curCore.array[curCore.size].compareAndSet(null, Value(element))){
                    core.value.inc(curSize)
                    return
                }
                core.value.inc(curSize)
            } else {
                move(curCore)
            }
        }
    }

    override val size: Int get() = core.value.size
}

private interface Wrapper<E>

private class InProcess<E>(val element: E) : Wrapper<E> //V' на лекции
private class Moved<E>(val element: E) : Wrapper<E> // S
private class Value<E>(val element: E) : Wrapper<E> //V

private class Core<E>(
    capacity: Int,
) {
    val array = atomicArrayOfNulls<Wrapper<E>>(capacity)
    private val _size = atomic(0)
    val next = atomic<Core<E>?>(null)

    val size: Int = _size.value

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): Wrapper<E> {
        require(index < size)
        return array[index].value as Wrapper<E>
    }

    fun put(index: Int, element: Wrapper<E>?, prevElement: Wrapper<E>): Boolean {
        require(index < size)
        if (array[index].compareAndSet(prevElement, element)) {
            return true
        }
        return false
    }

    fun inc(prevSize : Int): Boolean{
        return _size.compareAndSet(prevSize, prevSize + 1)
    }

}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME