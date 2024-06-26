package mpp.dynamicarray

import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls

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
    private val atomicSize = atomic(0)

    override fun get(index: Int): E {
        if(index >= size){
            throw IllegalArgumentException()
        }
        while (true) {
            val curArrayState = core.value
            if (index > curArrayState.capacity - 1)
                continue // TODO
            val prevElementWrapped= curArrayState.array[index].value
            if(prevElementWrapped != null){
                return prevElementWrapped.element.value
            }
        }
    }

    override fun put(index: Int, element: E) {
        if (index >= size) {
            throw IllegalArgumentException()
        }
        while (true) {
            val curArrayState = core.value
            if (index > curArrayState.capacity - 1)
                continue // TODO
            val prevElementWrapped= curArrayState.array[index].value
            if (prevElementWrapped != null) {
//                val prevElementRef = prevElementWrapped.element
                if (prevElementWrapped.element.compareAndSet(prevElementWrapped.element.value, element)
                    && curArrayState.array[index].value == prevElementWrapped ){
                    return
                }
            }
        }
    }

    override fun pushBack(element: E) {
        val curSize = atomicSize.getAndIncrement()
        while (true) {
            val curArrayState = core.value
            val prevElementRefToWrapped = curArrayState.array[curSize] // TODO
            if (curSize > curArrayState.capacity - 1) {
                resize(curArrayState)
            } else if (curArrayState.array[curSize].compareAndSet(null, CoreElement(atomic(element)))) {
                break
            }
        }
    }

    private fun resize(curArrayState: Core<E>) {
        val newArrayState = Core<E>(2 * curArrayState.capacity)
        if (curArrayState.next.compareAndSet(null, newArrayState)) {
            for (i in 0 until curArrayState.capacity) {
                while (true) {
                    val element = curArrayState.array[i].getAndSet(null)
                    if (element != null) {
                        newArrayState.array[i].compareAndSet(null, element)
                        break // TODO
                    }
                }
            }
            core.compareAndSet(curArrayState, newArrayState)     // TODO
        }
    }

    override val size: Int get() = atomicSize.value
}

private class CoreElement<T>(val element: AtomicRef<T>)

private class Core<E>(
    val capacity: Int,
    size: Int = 0
) {
    val array = atomicArrayOfNulls<CoreElement<E>>(capacity)
    val next: AtomicRef<Core<E>?> = atomic(null)

//    @Suppress("UNCHECKED_CAST")
//    fun get(index: Int): E {
//        require(index < atomicSize.value)
//        return array[index].value as E
//    }
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME
