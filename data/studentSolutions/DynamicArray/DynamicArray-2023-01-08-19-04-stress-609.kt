package mpp.dynamicarray

import kotlinx.atomicfu.*
import java.util.concurrent.atomic.AtomicBoolean

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
    private val lock = AtomicBoolean(false)

    override fun get(index: Int): E{
        checkSize(index)
        return core.value.get(index)
    }

    override fun put(index: Int, element: E) {
        checkSize(index)
        while (true){
            if (core.value.put(index, element)){
                return
            }
        }
    }

    override fun pushBack(element: E) {
        while (true) {
            if (tryLock()) {
                val index = core.value.resize()
                overrideTable()
                put(index, element)
                unlock()
                return
            }
        }
    }

    fun overrideTable(){
        val core = Core<E>(size)
        for (i in 0 until size){
            core.resize()
            put(i, get(i))
        }
        this.core.getAndSet(core)
    }

    fun checkSize(index: Int){
        if (index > size){
            throw IllegalArgumentException()
        }
    }

    private fun tryLock(): Boolean {
        return this.lock.compareAndSet(false, true)
    }

    private fun unlock(){
        this.lock.getAndSet(false)
    }

    override val size: Int get() = core.value.size
}

private class Core<E>(
    capacity: Int,
) {
    private val array = atomicArrayOfNulls<E>(capacity)
    private val _size = atomic(0)

    val size: Int = _size.value

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E {
        require(index < size)
        return array[index].value as E
    }

    fun put(index: Int, element: E): Boolean{
        return (array[index].compareAndSet(get(index), element))
    }

    fun resize(): Int {
        return _size.incrementAndGet()
    }
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME