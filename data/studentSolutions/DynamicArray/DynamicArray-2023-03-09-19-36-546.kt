//package mpp.dynamicarray

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


sealed class Movement<E> (open val value: E) {
    data class Still<E>(override val value: E) : Movement<E>(value)
    data class Moved<E>(override val value: E) : Movement<E>(value)
}

class DynamicArrayImpl<E> : DynamicArray<E> {
    private val core = atomic(Core<E>(INITIAL_CAPACITY, 0))

    override fun get(index: Int): E = core.value.get(index)

    override fun put(index: Int, element: E) {
        core.value.put(index, element)
    }

    // idea: pushbacks help each other
    // read? either from current or if the dedired element is moved, from nect
    // put? either in the current or in the next (unless it's also move, then repeat step 2)
    // most next.size mb
    override fun pushBack(element: E) {
        core.value.pushBack(element) // mb check itf it's still vible. also maybe redirect inside if the core is depricated
        val localCore = core.value
        if (localCore.deprecated) {
            core.compareAndSet(localCore, localCore.next.value!!)
        }
    }

    override val size: Int get() = core.value.size
}

private class Core<E>(
    val capacity: Int,
    val initSize: Int,
) {
    private val array = atomicArrayOfNulls<Movement<E>>(capacity)
    private val _size = atomic(initSize)
    val next = atomic<Core<E>?>(null)
    private val _deprecated = atomic(false)

    val size: Int = _size.value
    val deprecated: Boolean = _deprecated.value

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E {
        require(index < size)
        val value = array[index].value as Movement<E>
        when (value) {
            is Movement.Still<E> -> return value.value
            else -> {}
        }
        help()
        return next.value?.get(index) as E
    }

    fun put (index: Int, element: E) {
        require(index < size)
        while (true) {
            val value = array[index].value
            if (value == null) {
                if (array[index].compareAndSet(null, Movement.Still(element))) return
                continue
            }
            when (value) {
                is Movement.Moved<E> -> break
                else -> {}
            }
            if (array[index].compareAndSet(value, Movement.Still(element))) return
        }
        help()
        next.value?.put(index, element)
    }

    @Suppress("UNCHECKED_CAST")
    fun pushBack(element: E) {
        while (true) {
            val localSize = size
            if (localSize >= capacity) break
            if (array[localSize].compareAndSet(null, Movement.Still(element))) {
                _size.compareAndSet(localSize, localSize + 1)
                return
            } else {
                _size.compareAndSet(localSize, localSize + 1)
            }
        }
        val newCore = Core<E>(capacity shl 1, capacity + 1)
        newCore.array[capacity].value = Movement.Still(element)
        if (!next.compareAndSet(null, newCore)) {
            help()
            next.value?.pushBack(element)
        }
        /* copy the elements
        1) change the original from still to moved
        2) put it into the new array with cas

        */
        for (i in (0 until capacity)) {
            if (newCore.array[i].value != null) continue
            val value = array[i].getAndUpdate { movement -> when(movement) {is Movement.Still -> Movement.Moved(movement.value); else -> movement} }
                newCore.array[i].compareAndSet(null, Movement.Still(value!!.value))
        }
        _deprecated.value = true
        // and that's naive implementation done ig
        // not reallt, there' still need to make help do things and end only after the migration is ended
    }

    fun help() {
        if (deprecated) return
        val newCore = next.value!!
        for (i in (0 until capacity)) {
            if (newCore.array[i].value != null) continue
            val value = array[i].getAndUpdate { movement -> when(movement) {is Movement.Still -> Movement.Moved(movement.value); else -> movement} }
            newCore.array[i].compareAndSet(null, Movement.Still(value!!.value))
        }
        _deprecated.value = true
    }
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME