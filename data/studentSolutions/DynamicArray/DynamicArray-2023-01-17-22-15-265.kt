//package mpp.dynamicarray

import kotlinx.atomicfu.*
import java.lang.IllegalArgumentException

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
        var curCore = core.value
        while (true) {
            val value = curCore.get(index)!!
            if (value is Value || value is FixedValue) {
                return value.value!!
            } else {
                curCore = curCore.next.value!!
            }
        }
    }

    override fun put(index: Int, element: E) {
        var curCore = core.value
        while (true) {
            val value = curCore.get(index)!!
            if (value is Value) {
                if (curCore.array[index].compareAndSet(value, Value(element))) return
            } else {
                while (true) {
                    if (curCore.moveValue(Value(element), index)) {
                        return
                    }
                    curCore = curCore.next.value!!
                }
            }
        }
    }

    override fun pushBack(element: E) {
        while (true) {
            val curCore = core.value
            if (curCore.capacityExpandCondition()) {
                curCore.createNext()
                curCore.updateNext()
                updateCore()
                continue
            }
            val pushInd = curCore.size
            if (pushInd < curCore.capacity) {
                if (curCore.casPut(Value(element), pushInd)) {
                    return
                }
            }
        }
    }

    private fun updateCore() {
        while (true) {
            val curCore = core.value
            val nextCore = curCore.next.value
            if (nextCore != null && curCore.movedSize.value == curCore.capacity) {
                core.compareAndSet(curCore, nextCore)
            } else {
                break
            }
        }
    }

    override val size: Int get() = core.value.size
}

private class Core<E>(
    val capacity: Int,
) {
    val array = atomicArrayOfNulls<AbstractValue<E>>(capacity)
    val next : AtomicRef<Core<E>?> = atomic(null)
    private val _size = atomic(0)
    val movedSize = atomic(0)

    val size: Int get() = _size.value

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): AbstractValue<E>? {
        if (index >= size) {
            throw IllegalArgumentException()
        }
        return array[index].value
    }

    fun casPut(value: AbstractValue<E>, index : Int) : Boolean {
        while (true) {
            val oldValue = array[index].value
            if (oldValue != null && oldValue !is Value) {
                return false
            }
            if (array[index].compareAndSet(oldValue, value)) {
                casSizeTo(index + 1)
                return true
            }
        }
    }

    fun casSizeTo(index: Int) {
        while (true) {
            val curSize = _size.value
            if (curSize < index) {
                if (_size.compareAndSet(curSize, index)) {
                    return
                }
            } else {
                break
            }
        }
    }

    fun casMovedSizeTo(index: Int) {
        while (true) {
            val curMovedSize = movedSize.value
            if (curMovedSize < index) {
                if (movedSize.compareAndSet(curMovedSize, index)) {
                    return
                }
            } else {
                break
            }
        }
    }

    fun moveValue(value: AbstractValue<E>, index : Int) : Boolean {
        if (next.value!!.casPut(value, index)) {
            array[index].value = MovedValue()
            casMovedSizeTo(index + 1)
            return true
        }
        return false
    }

    fun capacityExpandCondition() : Boolean {
        return size * 2 > capacity
    }

    fun createNext() {
        while (next.value == null) {
            if (next.compareAndSet(null, Core(2 * capacity))) {
                break
            }
        }
    }

    fun updateNext() {
        while (true) {
            val index = movedSize.value
            if (index >= capacity) {
                break
            }
            val oldValue = array[index].value
            if (oldValue is MovedValue) {
                casMovedSizeTo(index + 1)
                continue
            }

            if (oldValue is FixedValue || fixValue(oldValue as Value<E>?, index)) {
                if (oldValue != null) {
                    moveValue(oldValue, index)
                }
            }
        }
    }

    fun fixValue(value: Value<E>?, index : Int) : Boolean {
        val newValue = if (value == null) MovedValue() else FixedValue(value.value)
        val result = array[index].compareAndSet(value, newValue)
        if (result && newValue is MovedValue) {
            casMovedSizeTo(index + 1)
        }
        return result
    }
}

interface AbstractValue<E> {
    val value : E?
}
open class Value<E> (override val value: E) : AbstractValue<E>
class FixedValue<E>(override val value: E) : AbstractValue<E>
class MovedValue<E> : AbstractValue<E> {
    override val value: E? = null
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME