//package mpp.dynamicarray

import kotlinx.atomicfu.*
import java.util.concurrent.atomic.AtomicInteger

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
    private val core = atomic(Core<E>(INITIAL_CAPACITY, 0))
    private val lock: AtomicBoolean = atomic(false)

    override fun get(index: Int): E {
        return getNodeByIndex(index).value
    }

    override fun put(index: Int, element: E) {
        while (true) {
            val curNode = getNodeByIndex(index)
            if (curNode.status == Status.DEFAULT && core.value.array[index].compareAndSet(
                    curNode,
                    Node(element, Status.DEFAULT)
                )
            ) {
                return
            }
        }
    }

    private fun getNodeByIndex(index: Int): Node<E> {
        if (index >= size || index < 0) throw IllegalArgumentException()
        return core.value.array[index].value ?: throw IllegalArgumentException()
    }

    private fun resize(curCore: Core<E>, curSize: Int) {
        if (lock.compareAndSet(expect = false, update = true)) {
            core.compareAndSet(curCore, copy(curCore, curSize))
            lock.compareAndSet(expect = true, update = false)
        }
    }

    private fun copy(curCore: Core<E>, curSize: Int): Core<E> {
        val nextCore = Core<E>(curCore.capacity * 2, curSize)
        for (i in 0 until curSize) {
            while (true) {
                val curNode = curCore.array[i].value ?: continue
                if (curCore.array[i].compareAndSet(curNode, Node(curNode.value, Status.COPIED))) {
                    nextCore.array[i].compareAndSet(null, curNode)
                    break
                }
            }
        }
        return nextCore
    }

    private fun push(curCore: Core<E>, curSize: Int, element: E): Boolean? {
        if (curCore.array[curSize].compareAndSet(null, Node(element, Status.DEFAULT))) {
            curCore.size.incrementAndGet()
            return null
        }
        return true
    }

    override fun pushBack(element: E) {
        while (true) {
            val curCore = core.value
            val (cap, curSize) = curCore
            if (curSize == cap) resize(curCore, curSize)
            else push(curCore, curSize, element) ?: return
        }
    }

    override val size: Int
        get() {
            return core.value.component2()
        }

    class Core<E>(
        val capacity: Int,
        initialSize: Int,
    ) {
        val size = AtomicInteger(initialSize)
        val array = atomicArrayOfNulls<Node<E>>(capacity)

        operator fun component1(): Int {
            return capacity
        }

        operator fun component2(): Int {
            return size.get()
        }
    }

    class Node<E>(
        val value: E,
        val status: Status
    )

    enum class Status {
        DEFAULT, COPIED
    }
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME