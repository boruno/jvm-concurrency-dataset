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
        core.value.set(index, element)
    }

    override fun pushBack(element: E) {
        while (true) {
            val currentCore = core.value
            if (currentCore.isNotFull()) {
                if (currentCore.pushBack(element)) {
                    return
                }
            } else if (currentCore.isFull()) {
                val newCore = Core<E>(currentCore.capacity * 2)
                for (i in 0 until currentCore.capacity) {
                    newCore.set(i, currentCore.get(i))
                }
                newCore._size.addAndGet(currentCore.capacity)
                core.compareAndSet(currentCore, newCore)
            } else {
                throw java.lang.IllegalStateException("Cant be")
            }
        }
    }

    override val size: Int
        get() {
            val size = core.value.size
            println(size)
            return size
        }

//    override val size: Int
//        get() {
//            val size = core.value.size
//            println(size)
//            try {
//                core.value.get(size)
//                throw java.lang.IllegalStateException("Cant be")
//            } catch (e: Exception) {
//                if (e is IllegalArgumentException) {
//                    return size
//                } else {
//                    throw e
//                }
//            }
//        }
}

private class Core<E>(
    val capacity: Int,
) {
    private val array = atomicArrayOfNulls<E>(capacity)
    val _size = atomic(0)

    val size: Int = _size.value

    fun isFull(): Boolean {
        return size == capacity
    }

    fun isNotFull(): Boolean {
        return size < capacity
    }

    fun pushBack(elem: E): Boolean {
        if (array[size].compareAndSet(null, elem)) {
            val before = size
            println("pushedb $before")
            val after = before + 1
            println("pusheda $after")
            if (_size.compareAndSet(before, after)) {
                println("sizeUpdated ${_size.value}")
                return true
            } else {
                throw IllegalArgumentException("cant be")
            }
        } else {
            return false
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E {
        require(index < size)
        return array[index].value as E
    }

    fun set(index: Int, elem: E) {
        require(index < size)
        array[index].value = elem
    }
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME