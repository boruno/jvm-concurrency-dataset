package mpp.dynamicarray

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
    private val taken: AtomicBoolean = atomic(false)

    private val core = atomic(Core<E>(INITIAL_CAPACITY))

    override fun get(index: Int): E {
        if (index >= size) throw IllegalArgumentException("Index out of range: $index")
        val staticCore = core.value
        val currentNode: Node<E> =
            (staticCore.array[index].value ?: throw IllegalArgumentException("Index out of range: $index"))
        return currentNode.value
    }

    override fun put(index: Int, element: E) {
        while (true) {
            if (index >= size) throw IllegalArgumentException("Index out of range: $index")
            val staticCore = core.value
            val staticValue =
                staticCore.array[index].value ?: throw IllegalArgumentException("Index out of range: $index")
            if (staticValue.status == Status.PROGRESS) {
                continue
            }
            if (core.value.array[index].compareAndSet(staticValue, Node(element, Status.STAY))) {
                return
            }
        }
    }

    override fun pushBack(element: E) {
        while (true) {
            val staticCore = core.value
            val staticSize = staticCore.size.get()
            if (staticSize == staticCore.capacity) {
                resize(staticCore, staticSize)
            } else {
                if (staticCore.array[staticSize].compareAndSet(expect = null, update = Node(element, Status.STAY))) {
                    staticCore.size.incrementAndGet()
                    return
                }
            }
        }
    }

    private fun resize(staticCore : Core<E>, staticSize : Int) {
        if (taken.compareAndSet(expect = false, update = true)) {
            val tempCore = Core<E>(staticCore.capacity * 2)
            for (i in 0 until staticSize) {
                while (true) {
                    val value = staticCore.array[i].value ?: continue
                    val valueNew = Node(value.value, Status.PROGRESS)
                    if (staticCore.array[i].compareAndSet(expect = value, update = valueNew)) {
                        tempCore.array[i].compareAndSet(expect = null, update = value)
                        break
                    }
                }
            }
            tempCore.size.compareAndSet(0, staticSize)
            core.compareAndSet(staticCore, tempCore)
            taken.compareAndSet(expect = true, update = false)
        }
    }

    override val size: Int
        get() {
            return core.value.size.get()
        }

}

class Node<E>(
    val value: E,
    val status: Status
)

enum class Status {
    STAY, PROGRESS
}

private class Core<E>(
    val capacity: Int,
) {
    val array = atomicArrayOfNulls<Node<E>>(capacity)
    val size = AtomicInteger(0)
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME
