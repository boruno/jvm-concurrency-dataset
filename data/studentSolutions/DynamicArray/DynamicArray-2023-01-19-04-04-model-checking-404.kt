package mpp.dynamicarray

import kotlinx.atomicfu.*
import java.lang.NullPointerException

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
    private val core = atomic(Core<E>(INITIAL_CAPACITY, 0))

    @Suppress("UNCHECKED_CAST")
    override fun get(index: Int): E {
        var curCore = core.value
        var result = curCore.get(index)

        if (result !is FixedValue<*>) return result as E
        try {
            while (true) {
                curCore = curCore.nextCore.value!!
                result = curCore.get(index)
                if (result !is FixedValue<*>) return result as E
            }
        } catch (e: NullPointerException) {
            return result.value as E
        }
    }

    override fun put(index: Int, element: E) {
        while (true) {
            val curCore = core.value
            while (true) {
                val result = curCore.put(index, element)
                if (result !is FixedValue<*>) {
                    if ((result as String) == "SUCCESS") return
                    continue
                }
                break
            }

            val nextCore = curCore.nextCore.value!!
            if (nextCore.tryPutWhileTransfer(index, element)) return
        }
    }


    override fun pushBack(element: E) {
        while (true) {
            val curCore = core.value
            val result = curCore.pushBack(element)
            if (result is String) {
                return
            }
            val newCore = Core<E>((result as Int) * 2, result)
            curCore.nextCore.compareAndSet(null, newCore)
            curCore.transferExistedValues()
            core.compareAndSet(curCore, curCore.nextCore.value!!)
        }
    }

    override val size: Int get() = core.value.size
}

private class Core<E>(
    capacity: Int, __size: Int
) {
    private val array = atomicArrayOfNulls<Any?>(capacity)
    private val _size = atomic(__size)
    val nextCore: AtomicRef<Core<E>?> = atomic(null)

    val size: Int get() = _size.value

    fun get(index: Int): Any {
        require(index < size)
        return array[index].value!!
    }

    fun put(index: Int, element: E): Any {
        val currentValue = get(index)
        if (currentValue is FixedValue<*>) return currentValue
        if (array[index].compareAndSet(currentValue, element)) {
            return "SUCCESS"
        }
        return "FAIL"
    }

    fun pushBack(element: E): Any {
        var currentSize = size
        while (true) {
            if (currentSize >= array.size) {
                return currentSize
            }

            if (array[currentSize].compareAndSet(null, element)) {
                _size.getAndIncrement()
                return "SUCCESS"
            }
            currentSize++
        }
    }

    fun tryPutWhileTransfer(index: Int, element: E): Boolean {
        while (true) {
            val result = array[index].value
            if (result is FixedValue<*>) return false
            if (array[index].compareAndSet(result, element)) return true
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun transferExistedValues() {
        outerfor@ for (i in 0 until  array.size) {
            var result = array[i].value
            while (true) {
                if (result is FixedValue<*>) {
                    result = result.value
                    break
                }
                if (array[i].compareAndSet(result, FixedValue(result))) break
                result = array[i].value
            }

            nextCore.value!!.tryPushInTransfer(i, result as E)
        }
    }

    fun tryPushInTransfer(index: Int, element: E) {
        array[index].compareAndSet(null, element)
    }
}

private class FixedValue<E>(val value: E)

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME