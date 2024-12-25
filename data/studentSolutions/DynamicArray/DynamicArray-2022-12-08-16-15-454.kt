//package mpp.dynamicarray

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

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
    private var core = AtomicReference(Core<E>(INITIAL_CAPACITY))

    override fun get(index: Int): E {
        require(index < core.get().getSize())
        val coreNow = core.get()
        if (coreNow.isMoved) {
            core.compareAndSet(coreNow, coreNow.next.get())
        }
        return core.get().get(index)
    }

    override fun put(index: Int, element: E) {
        val coreNow = core.get()
        if (coreNow.isMoved) {
            core.compareAndSet(coreNow, coreNow.next.get())
        }
        core.get().put(element, index)
    }

    override fun pushBack(element: E) {
        val coreNow = core.get()
        if (coreNow.isMoved) {
            core.compareAndSet(coreNow, coreNow.next.get())
        }
        println(60)
        core.get().put(element)
    }

    override val size: Int get() = core.get().getSize()
}

private class Core<E>(
    val capacity: Int,
    private val size: AtomicInteger = AtomicInteger(0)
) {
    private val array = Array<AtomicReference<Node<E>>>(capacity) {
        AtomicReference(Empty())
    }

    val next = AtomicReference<Core<E>?>(null)

    private val isMoving = AtomicBoolean(false)

    var isMoved = false

    fun getSize(): Int = size.get()

    fun get(index: Int): E {
        return if (index < capacity) {
            when (val node = array[index].get()) {
                is Empty -> {
                    throw Exception("Trying to access empty node.")
                }

                is Moved -> {
                    next.get()!!.get(index)
                }

                is WithValueTemp -> {
                    node.value
                }

                is WithValue -> {
                    node.value
                }
            }
        } else {
            next.get()!!.get(index)
        }
    }

    private fun moveArrayElementsToExistingNextCore() {
        if (!isMoving.compareAndSet(false, true)) {
            return
        }
        try {
            for (i in 0 until capacity) {
                val nodeRef = array[i]

                /// marks current array as it is now temporary and shouldn't be accessed for writes
                var flag = false
                while (true) {
                    val node = nodeRef.get()
                    if (node is Empty) {
                        nodeRef.compareAndSet(node, Moved())
                        flag = true
                        break
                    }
                    val valueInside = (node as WithValue<E>).value
                    if (nodeRef.compareAndSet(node, WithValueTemp(valueInside))) {
                        break
                    }
                }
                if (flag) continue

                /// copies current temporary value into next array in appropriate cell
                val valueInside = (nodeRef.get() as WithValueTemp<E>).value
                next.get()!!.casAssistance(valueInside, i, Empty())

                /// marks current cell as already moved and so all writes and reads should call next Core
                val nodeValue = nodeRef.get()
                nodeRef.compareAndSet(nodeValue, Moved())
            }
            isMoved = true

        } finally {
            isMoving.set(false)
        }

    }

    private fun createNextCoreIfNeeded(indexToPush: Int) {
        if (indexToPush >= capacity) {
            while (next.get() == null) {
                val newVectorCore = Core<E>(2 * capacity, size)
                if (next.compareAndSet(null, newVectorCore)) {
                    moveArrayElementsToExistingNextCore()
                }
            }
        }
    }

    fun casAssistance(elem: E, index: Int = -1, oldValueNeeded: Node<E>? = null): Boolean {
        println(159)
        val indexToPush = if (index == -1) {
            size.getAndIncrement()
        } else {
            index
        }.toInt()
        println(165)
        println(indexToPush)
        createNextCoreIfNeeded(indexToPush)
        println(168)
        if (indexToPush >= capacity) {
            return next.get()!!.put(elem, indexToPush)
        }
        println(172)
        val oldValue = oldValueNeeded ?: array[indexToPush].get()
        if (oldValue is Moved || oldValue is WithValueTemp) {
            return next.get()!!.put(elem, indexToPush)
        }
        println(177)
        println(elem)
        return array[indexToPush].compareAndSet(oldValue, WithValue(elem))
    }

    fun put(elem: E, index: Int = -1): Boolean {
        return casAssistance(elem, index)
    }
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME
