package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.EmptyStackException
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.atomic.AtomicStampedReference

private class LockFreeExchanger<T> {
    companion object {
        const val EMPTY = 0
        const val WAITING = 1
        const val BUSY = 2
    }

    private val slot = AtomicStampedReference<T>(null, EMPTY)

    fun exchange(myItem: T?): T? {
        val stampHolder = intArrayOf(EMPTY)

        for (j in 0..10) {
            var yrItem = slot.get(stampHolder)
            when (stampHolder[0]) {
                EMPTY -> {
                    if (slot.compareAndSet(yrItem, myItem, EMPTY, WAITING)) {
                        for (i in 0..10) {
                            yrItem = slot.get(stampHolder)
                            if (stampHolder[0] == BUSY) {
                                slot.set(null, EMPTY)
                                return yrItem
                            }
                        }

                        if (slot.compareAndSet(myItem, null, WAITING, EMPTY)) {
                            return null // ???
                        } else {
                            yrItem = slot.get(stampHolder)
                            slot.set(null, EMPTY)
                            return yrItem
                        }
                    }
                }

                WAITING -> {
                    if (slot.compareAndSet(yrItem, myItem, WAITING, BUSY)) {
                        return yrItem
                    }
                }

                BUSY -> {}
                // else is unreachable
            }
        }
        return null
    }
}

class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<LockFreeExchanger<E>>(ELIMINATION_ARRAY_SIZE)

    init {
        for (i in 0 until ELIMINATION_ARRAY_SIZE) {
            eliminationArray[i].value = LockFreeExchanger()
        }
    }

    private fun visit(value: E?): E? {
        val slot = ThreadLocalRandom.current().nextInt(ELIMINATION_ARRAY_SIZE)
        return eliminationArray[slot].value?.exchange(value)
    }

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        val node = Node(x, null)
        while (true) {
            if (tryPush(node)) {
                return
            } else {
                visit(x)
                    ?: return // exchanged with pop
            }
        }
    }

    private fun tryPush(node: Node<E>): Boolean {
        val oldTop = top.value
        node.next = oldTop
        return (top.compareAndSet(oldTop, node))
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
        while (true) {
            try {
                val returnNode: Node<E>? = tryPop() // throws Exception
                if (returnNode != null) {
                    return returnNode.x
                } else {
                    val otherValue: E? = visit(null)
                    if (otherValue != null) {
                        return otherValue
                    }
                }
            } catch (e: EmptyStackException) {
                return null
            }
        }
    }

    private fun tryPop(): Node<E>? {
        val oldTop = top.value ?: throw EmptyStackException()
        val newTop = oldTop.next
        return if (top.compareAndSet(oldTop, newTop)) {
            oldTop
        } else {
            null
        }
    }
}

private class Node<E>(val x: E, var next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT