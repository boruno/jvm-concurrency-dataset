package mpp.dynamicarray

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

    override fun get(index: Int): E = core.value.get(index)

    override fun put(index: Int, element: E) {
        if(index>=core.value.getSize()) {
            throw IllegalArgumentException()
        }

        val value = core.value.array[index].value
        if(!core.value.next.compareAndSet(null,null)){
            if(value!=null && !value.moving.value){
                if(value.moving.compareAndSet(false,true)){
                    val next = core.value.next.value
                    var newValue = value
                    newValue.moving.compareAndSet(true,false)
                    next!!.array[index].compareAndSet(null,newValue)
                    next.increaseSize()
                    return
                }
            }
        }
        core.value.array[index].compareAndSet(value,Cell(element))
    }

    override fun pushBack(element: E) {
        while (true) {
            if (size >= core.value.capacity) {
                moveCore()
            }
            val core = this.core.value
            val value = core.array[size]
            if (size >= core.capacity) {
                moveCore()
            }
            if(core.array[size].compareAndSet(null, Cell(element))){
                this.core.value.increaseSize()
                return
            }
        }
    }

    private fun moveCore() {
        val core = this.core
        val next = Core<E>(core.value.capacity * 2)
        if (!core.value.next.compareAndSet(null, next)) {
            return
        }
        for(i in 0 until core.value.capacity){
            val valueOfCell = core.value.array[i].value
            val valueOfCellInNew = core.value.next.value!!.array[i]
            if(valueOfCell!=null){
                if(valueOfCell.moving.compareAndSet(false,true)){
                    valueOfCellInNew.compareAndSet(null,valueOfCell)
                    valueOfCellInNew.value!!.moving.compareAndSet(true,false)
                    next.increaseSize()
                }
            }
        }
        this.core.compareAndSet(this.core.value, this.core.value.next.value!!)
    }

    override val size: Int get() = core.value.getSize()
}

private class Core<E>(
    val capacity: Int,
) {
    val array = atomicArrayOfNulls<Cell<E>>(capacity)
    val _size = atomic(0)
    val next: AtomicRef<Core<E>?> = atomic(null)

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E {
        require(index < _size.value)
        return array[index].value as E
    }

    fun increaseSize() {
        _size.getAndIncrement()
    }
    fun getSize(): Int {
        return _size.value
    }
}

private class Cell<E>(val content: E) {
    val moving: AtomicBoolean = atomic(false)
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME