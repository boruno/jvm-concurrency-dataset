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

    private val emptyBlock = {}
    private val core = atomic(Core<E>(INITIAL_CAPACITY))

    override val size: Int
        get() {
            return currentState.size.value
        }

    override fun get(index: Int): E {
        while (true) {
            val curState = currentState
            curState.checkIndex(index)
            val curNode = curState.array[index].value!!
            val curValue = curNode.value
            return when (curNode !is Fixed) {
                true -> curValue
                else -> casNext(curState, index, curValue) { curValue } ?: continue
            }
        }
    }

    override fun put(index: Int, element: E) {
        while (true) {
            val curState = currentState
            curState.checkIndex(index)
            val newNode = Node(element)
            val old = curState.array[index].value
            return when (old !is Fixed) {
                true -> when (curState.array[index].compareAndSet(old, newNode)) {
                    true -> return
                    else -> continue
                }
                else -> casNext(curState, index, element, emptyBlock) ?: continue
            }
        }
    }

    override fun pushBack(element: E) {
        while (true) {
            val curState = currentState
            val size_ = curState.size.value
            val newNode = Node(element)
            if (size_ >= curState.capacity) {
                if (!ensureCapacity(curState)) {
                    continue
                }
            } else if (curState.array[size_].compareAndSet(null, newNode)) {
                curState.incrementSize(size_)
                return
            }
        }
    }

    private val currentState: Core<E>
        get() {
            return core.value
        }

    private fun <Result> casNext(state: Core<E>, index: Int, value: E, completionBlock: () -> Result): Result? {
        if (state.next.value!!.array[index].compareAndSet(null, Node(value))) {
            return completionBlock()
        }
        return null
    }

    private fun ensureCapacity(state: Core<E>): Boolean {
        if (state.next.compareAndSet(null, Core(2 * state.capacity))) {
            for (i in 0 until state.capacity) {
                while (true) {
                    val old = state.array[i].value
                    val value = old!!.value
                    val fixed = Fixed(value)
                    if (state.array[i].compareAndSet(old, fixed)) {
                        casNext(state, i, value, emptyBlock)
                        break
                    }
                }
            }
            core.compareAndSet(state, state.next.value!!)
            return true
        }
        return false
    }
}

open class Node<E>(val value: E)
private class Fixed<E>(fixedValue: E) : Node<E>(fixedValue)

private class Core<E>(val capacity: Int) {

    val array: AtomicArray<Node<E>?> = atomicArrayOfNulls(capacity)
    val next: AtomicRef<Core<E>?> = atomic(null)
    val size: AtomicInt = atomic(capacity / 2)

    fun incrementSize(currentSize: Int) {
        when (!size.compareAndSet(currentSize, currentSize + 1)) {
            true -> incrementSize(currentSize)
            else -> return
        }
    }

    fun checkIndex(index: Int) {
        if (index >= this.size.value) {
            throw IllegalArgumentException()
        }
    }
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME