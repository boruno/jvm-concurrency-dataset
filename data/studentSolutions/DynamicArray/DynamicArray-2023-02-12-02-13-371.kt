//package mpp.dynamicarray

import kotlinx.atomicfu.*
import java.util.concurrent.atomic.AtomicMarkableReference
import java.util.concurrent.atomic.AtomicReferenceArray

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
    private val core = atomic(Core<E>(capacity = INITIAL_CAPACITY))
    private val s = atomic(0)

    private fun ch(index: Int) {
        if (index >= size)
            throw IllegalArgumentException()
    }

    override fun get(index: Int): E {
        ch(index)
        return core.value.arr[index].value!!.value
    }

    override fun put(index: Int, element: E) {
        ch(index)
        var currC = core.value

        while (true) {
            when (val cur = currC.arr[index].value!!) {
                is Bas -> {
                    if (currC.arr[index].compareAndSet(cur, Bas(element)))
                        return
                }
                is Fix, is Mov -> {
                    mov(index)
                    val currN = currC.next.value!!
                    core.compareAndSet(currC, currN)
                    currC = core.value
                }
            }
        }
    }

    override fun pushBack(element: E) {
        var currC = core.value

        while (true) {
            val currS = size
            val currCapacity = currC.capacity
            if (currS < currCapacity) {
                if (currC.arr[currS].compareAndSet(null, Bas(element))) {
                    s.incrementAndGet()
                    return
                }
            } else {
                if (currC.next.compareAndSet(null, Core(2 * currCapacity))){
                    mov(0)
                    core.compareAndSet(currC, currC.next.value!!)
                }
                currC = core.value
            }
        }
    }

    override val size: Int
        get() {
            return s.value
        }

    private fun mov(index: Int) {
        val currC = core.value
        val nextC = currC.next.value ?: return

        var ind = index
        while (ind < currC.capacity) {
            when (val current = currC.arr[ind].value!!) {
                is Mov -> ++ind
                is Bas -> {
                    val fixVal = Fix(current)
                    if (currC.arr[ind].compareAndSet(current, fixVal)) {
                        nextC.arr[ind].compareAndSet(null, current)
                        currC.arr[ind++].compareAndSet(fixVal, Mov(current))
                    }
                }
                is Fix -> {
                    nextC.arr[ind].compareAndSet(null, Bas(current))
                    currC.arr[ind++].compareAndSet(current, Mov(current))
                }
            }
        }
    }
}


private class Core<E>(val capacity: Int) {
    val arr = atomicArrayOfNulls<Wrapper<E>>(capacity)
    val next = atomic<Core<E>?>(null)
}

private open class Wrapper<E>(val value: E)

private class Fix<E>(e: Wrapper<E>) : Wrapper<E>(e.value)

private class Mov<E>(e: Wrapper<E>) : Wrapper<E>(e.value)

private class Bas<E>(e: Wrapper<E>) : Wrapper<E>(e.value) {
    constructor(value: E) : this(Wrapper(value))
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME