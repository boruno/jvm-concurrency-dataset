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

    private val core = atomic<Core<E>>(Core(INITIAL_CAPACITY))

    private fun moveArray(core_ : Core<E>) {
        if (core_.status == ArrayStatus.Deprecated) {
            core.compareAndSet(core_, core_.next.value!!)
            return
        }

        core_.setStatusMoving()
        if (core_.next.value == null) {
            val p_next = Core<E>(core_.capacity * 2)
            core_.next.compareAndSet(null, p_next)
        }

        for (i in (0 until core_.capacity)) {
            core_.fixValue(i)
            core_.next.value!!.putIfNull(i, core_.get(i))
        }
        core_.setStatusDeprecated()
        core.compareAndSet(core_, core_.next.value!!)
    }

    override fun get(index: Int): E = core.value.get(index)!!

    override fun put(index: Int, element: E) {
        var core_ = core.value
        while (!core_.put(index, element)) {
            moveArray(core_)
            core_ = core.value
        }
    }

    override fun pushBack(element: E) {
        while (true) {
            val core_ = core.value
            if (core_.pushBack(element))
               return
            moveArray(core_)
        }
    }

    override val size: Int get() = core.value.size
}

enum class ArrayStatus {
    Working, Moving, Deprecated
}

private class Core<E>(
    val capacity: Int,
) {

    data class CoreClass<E> (val data : E) {
        var fixed = false
    }

    private val array = atomicArrayOfNulls<CoreClass<E>>(capacity)
    private val _status = atomic(ArrayStatus.Working)
    private val _size = atomic(0)
    val next = atomic<Core<E>?>(null)
    val size: Int get() =  _size.value
    val status : ArrayStatus = _status.value

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E {
        require(index < size)
        return array[index].value as E
    }

    fun setStatusMoving() = _status.compareAndSet(ArrayStatus.Working, ArrayStatus.Moving)
    fun setStatusDeprecated() = _status.compareAndSet(ArrayStatus.Moving, ArrayStatus.Deprecated)

    fun put (index: Int, element: E): Boolean {
        require(index < size)
        while (true) {
            val cur = array[index].value
            if (cur!!.fixed) {
                return false
            }
            val clone = CoreClass(element)
            if (array[index].compareAndSet(cur, clone))
                return true
        }
    }

    fun fixValue(index : Int) {
        while (true) {
            if (array[index].value!!.fixed)
                return
            val value = array[index].value
            val newValue = value!!.copy()
            newValue.fixed = true
            if (array[index].compareAndSet(value, newValue))
                return
        }
    }

    fun putIfNull(index : Int, element: E){
        array[index].compareAndSet(null, CoreClass(element))
    }

    fun pushBack(element : E) : Boolean {
        while (true) {
            val curSize = size
            if (curSize < capacity) {
                if (array[curSize].compareAndSet(null, CoreClass(element))) {
                    _size.compareAndSet(curSize, curSize + 1)
                    return true
                } else {
                    _size.compareAndSet(curSize, curSize + 1)
                }
            } else {
                return false
            }
        }
    }
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME