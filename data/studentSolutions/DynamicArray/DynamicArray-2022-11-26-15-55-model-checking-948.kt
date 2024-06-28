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

    override fun get(index: Int): E = core.value.get(index)

    override fun put(index: Int, element: E) {
        if (index >= size) {
            throw IllegalArgumentException("index overflow");
        }
        while (!core.value.put(index, element)) {
        }
    }

    override fun pushBack(element: E) {
        while (true) {
            //resetCore()
            val index = core.value.size
            //if (index == core.value.capacity) continue
            if (!core.value.push(index, null, element)) continue
            require(core.value.size > 0)
            break
        }
    }

    private fun resetCore() {
        val currentCore = core.value
        if (currentCore.size == currentCore.capacity) {
            val newCore = Core<E>(currentCore.capacity * 2)
            for (i in 0 until currentCore.size) {
                newCore.push(i, null, currentCore.get(i))
            }
            core.compareAndSet(currentCore, newCore);
        }
    }

    override val size: Int get() = core.value.size
}

private class Core<E>(
    capacity: Int,
) {
    private val array = atomicArrayOfNulls<E>(capacity)
    private val _size = atomic(0)

    val size: Int = _size.value
    val capacity: Int = array.size

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E {
        require(index < size)
        return array[index].value as E
    }

    fun CAS(index: Int, old: E?, new: E?): Boolean {
        require(index < capacity)
        return array[index].compareAndSet(old, new)
    }

    fun push(index: Int, old: E?, new: E?): Boolean {
        if (!CAS(index, old, new)) return false
        _size.incrementAndGet()
        return true
    }

    fun put(index: Int, element: E?): Boolean {
        require(index < size)
        val value: E? = array[index].value ?: return false
        return CAS(index, value, element)
    }

}


private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME