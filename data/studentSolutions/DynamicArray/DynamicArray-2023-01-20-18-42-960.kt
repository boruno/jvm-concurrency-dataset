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

class DynamicArrayImpl<E> : DynamicArray<E> {
    private val core = atomic(Core<E>(INITIAL_CAPACITY))
    private val _size = atomic(0) // moved there for easier access and change

    override fun get(index: Int): E {
        require(index in 0 until size) { "Index $index is out of bounds" }
        while (true) {
            when (val value = core.value.get(index)) {
                is Move<E> -> copy()
                is Real<E> -> return value.elem
                else -> throw IllegalStateException("Unexpected value: $value")
            }
        }
    }

    private fun copy() {
        val currCore = core.value
        if (size < currCore.capacity) return
        currCore.nextCas(null, Core(currCore.capacity * 2))
        for (index in 0 until currCore.capacity) {
            var nextValue = currCore.next!!.get(index)
            var currValue = currCore.get(index)
            while (currValue is Real<E>) {
                if (currCore.next!!.elementCas(index, nextValue, currValue) &&
                    currCore.elementCas(index, currValue, Move())
                ) break
                nextValue = currCore.next!!.get(index)
                currValue = currCore.get(index)
            }
        }
        core.compareAndSet(currCore, currCore.next!!)
    }

    override fun put(index: Int, element: E) {
        require(index in 0 until size) { "Index $index is out of bounds" }
        while (true) {
            when (val value = core.value.get(index)) {
                is Move<E> -> copy()
                is Real<E> -> {
                    if (core.value.elementCas(index, value, Real(element))) return
                }
                else -> throw IllegalStateException("Unexpected value: $value")
            }
        }
    }

    override fun pushBack(element: E) {
        while (true) {
            val currCore = core.value
            val curSize = size
            if (curSize < currCore.capacity) {
                _size.compareAndSet(curSize, curSize + 1)
                if (currCore.elementCas(curSize, null, Real(element))) {
                    return
                }
            } else copy()
        }
    }

    override val size: Int get() = _size.value

}

private open class Core<E>(
    capacity: Int,
) {
    private val array = atomicArrayOfNulls<State<E>>(capacity)
    private val nextCore: AtomicRef<Core<E>?> = atomic(null)

    val capacity: Int get() = array.size

    val next: Core<E>? get() = nextCore.value

    fun get(index: Int): State<E>? {
        require(index in 0 until capacity) { "Index $index is out of bounds" }
        return array[index].value
    }

    fun nextCas(curr: Core<E>?, next: Core<E>): Boolean = this.nextCore.compareAndSet(curr, next)

    fun elementCas(index: Int, curr: State<E>?, next: State<E>?): Boolean =
        array[index].compareAndSet(curr, next)

}

private interface State<E>

private class Real<E>(val elem: E) : State<E>

private class Move<E> : State<E>

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME