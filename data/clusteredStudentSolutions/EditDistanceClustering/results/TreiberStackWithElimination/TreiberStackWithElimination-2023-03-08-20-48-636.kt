package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.EmptyStackException
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicStampedReference


private class LockFreeExchanger<T> {
    companion object{
        const val EMPTY = 0
        const val WAITING = 1
        const val BUSY = 2
    }

    private val slot = AtomicStampedReference<T>(null, 0)


    fun exchange(myItem: T?, timeout: Long, unit: TimeUnit): T {
        val nanos = unit.toNanos(timeout)
        val timeBound = System.nanoTime() + nanos

        val stampHolder = intArrayOf(EMPTY)

        while (true) {
            if (System.nanoTime() > timeBound) {
                throw TimeoutException()
            }

            var yrItem = slot.get(stampHolder)
            when (stampHolder[0]) {
                EMPTY -> {
                    if (slot.compareAndSet(yrItem, myItem, EMPTY, WAITING)) {
                        while (System.nanoTime() < timeBound) {
                            yrItem = slot.get(stampHolder)
                            if (stampHolder[0] == BUSY) {
                                slot.set(null, EMPTY)
                                return yrItem
                            }
                        }
                    }
                    if (slot.compareAndSet(myItem, null, WAITING, EMPTY)) {
                        throw TimeoutException()
                    } else {
                        yrItem = slot.get(stampHolder)
                        slot.set(null, EMPTY)
                        return yrItem
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
    }
}

class TreiberStackWithElimination<E> {

    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<LockFreeExchanger<E>>(ELIMINATION_ARRAY_SIZE)

    private val duration = 1000L // choose a number

    init {
        for (i in 0 until ELIMINATION_ARRAY_SIZE) {
            eliminationArray[i].value = LockFreeExchanger()
        }
    }

    private fun visit(value : E?) : E? {
        val slot = ThreadLocalRandom.current().nextInt(ELIMINATION_ARRAY_SIZE)
        return eliminationArray[slot].value?.exchange(value, duration, TimeUnit.MILLISECONDS)
    }

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        val node = Node(x, null)
        while (true) {
            if (tryPush(node)) {
                return
            }
            else {
                visit(x)
                    ?: return // exchanged with pop
            }
        }
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
        while (true) {
            val returnNode : Node<E>? = tryPop()
            if (returnNode != null) {
                return returnNode.x
            } else {
                val otherValue : E? = visit(null)
                if (otherValue != null) {
                    return otherValue
                }
            }
        }
    }

    private fun tryPush(node : Node<E>) : Boolean {
        val oldTop = top.value
        node.next = oldTop
        return (top.compareAndSet(oldTop, node))
    }

    private fun tryPop () : Node<E>? {
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