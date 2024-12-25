//package mpp.dynamicarray

import kotlinx.atomicfu.*
import java.lang.IllegalArgumentException
import java.lang.RuntimeException

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
    private val head: AtomicRef<Core<E>> = atomic(Core(INITIAL_CAPACITY, 0, null))

    private fun check(index: Int) {
        if (index < 0 || index > size) {
            throw IllegalArgumentException()
        }
    }

    override fun get(index: Int): E {
        check(index)
        val curHead = head.value
        val x = curHead.get(index)
        if (x != null) {
            return x
        } else throw RuntimeException()
    }

    override fun put(index: Int, element: E) {
        check(index)
        var curHead = head.value
        curHead.getAndSet(index, element)
        while (true) {
            val nextNode = curHead.next.value
            if (nextNode != null) {
                val y = curHead.get(index)
                if (y != null) {
                    nextNode.getAndSet(index, y)
                }
                curHead = nextNode
            } else return
        }
    }

    override fun pushBack(element: E) {
        while (true) {
            val curHead = head.value
            val curSize = size
            val curSize2 = curSize + 1
            if (curSize < curHead.getCapacity()) {
                if (curHead.compareAndSet(curSize, null, element)) {
                    curHead.len.compareAndSet(curSize, curSize2)
                    return
                } else {
                    curHead.len.compareAndSet(curSize, curSize2)
                }
            } else {
                val newNode =
                    Core<E>(2 * curHead.getCapacity(), curHead.getCapacity(), null)
                if (curHead.next.compareAndSet(null, newNode)) {
                    for (i in 0..curHead.getCapacity()-1) {
                        val y = curHead.get(i)
                        if (y != null) {
                            newNode.compareAndSet(i, null, y)
                        }
                    }
                    head.compareAndSet(curHead, newNode)
                } else {
                    val nextNode = curHead.next.value
                    if (nextNode != null) {
                        for (i in 0..curHead.getCapacity()-1) {
                            val y = curHead.get(i)
                            if (y != null) {
                                nextNode.compareAndSet(i, null, y)
                            }
                        }
                        head.compareAndSet(curHead, nextNode)
                    }
                }
            }
        }
    }

    override val size: Int
        get() {
            return head.value.len.value
        }
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME

class Core<E>(private val capacity: Int, length: Int, next: Core<E>?) {
    val array: AtomicArray<E?> = atomicArrayOfNulls<E>(capacity)
    val len: AtomicInt = atomic(length)
    val next: AtomicRef<Core<E>?> = atomic(next)

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E {
        return array[index].value as E
    }

    @Suppress("UNCHECKED_CAST")
    fun getAndSet(index: Int, value: E): E? {
        return array[index].getAndSet(value)
    }

    fun compareAndSet(index: Int, expect: E? = null, update: E): Boolean{
        return array[index].compareAndSet(expect, update)
    }

    fun getCapacity(): Int {
        return capacity
    }
}