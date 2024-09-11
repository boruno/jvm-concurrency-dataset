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
                if (curCore.casFirst(Value(element), pushInd)) {
                    return
                }
            }
        }
    }

    private fun updateCore() {
        while (true) {
            val curCore = core.value
            val nextCore = curCore.next.value
            if (nextCore != null && curCore.movedToNext.value == curCore.capacity) {
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
    val movedToNext = atomic(0)

    val size: Int get() = _size.value

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): AbstractValue<E>? {
        require(index < size)
        return array[index].value
    }

    fun casFirst(value: AbstractValue<E>, index : Int) : Boolean {
        if (array[index].compareAndSet(null, value)) {
            _size.incrementAndGet()
            return true
        }
        return false
    }

    fun casPut(value: AbstractValue<E>, index : Int) : Boolean {
        while (true) {
            val oldValue = array[index].value
            if (oldValue !is Value) {
                return false
            }
            if (array[index].compareAndSet(oldValue, value)) {
                return true
            }
        }
    }

    fun moveFirstValue(value: AbstractValue<E>, index : Int) : Boolean {
        if (next.value!!.casFirst(value, index)) {
            array[index].value = MovedValue()
            movedToNext.incrementAndGet()
            return true
        }
        return false
    }

    fun moveValue(value: AbstractValue<E>, index : Int) : Boolean {
        if (moveFirstValue(value, index)) {
            return true
        }
        return next.value!!.casPut(value, index)
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
            val ind = movedToNext.value
            if (ind >= capacity) {
                break
            }
            val oldValue = array[ind].value
            if (oldValue is MovedValue) {
                continue
            }

            if (oldValue is FixedValue || fixValue(oldValue as Value<E>?, ind)) {
                if (oldValue != null) {
                    moveFirstValue(oldValue, ind)
                }
            }
        }
    }

    fun fixValue(value: Value<E>?, i : Int) : Boolean {
        val newValue = if (value == null) MovedValue() else FixedValue(value.value)
        val result = array[i].compareAndSet(value, newValue)
        if (result && newValue is MovedValue) {
            movedToNext.incrementAndGet()
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