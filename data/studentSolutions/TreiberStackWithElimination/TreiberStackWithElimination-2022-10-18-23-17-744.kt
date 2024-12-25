//package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.concurrent.ThreadLocalRandom

class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<Any?>(ELIMINATION_ARRAY_SIZE)

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        val isExchanged = exchangePush(x)

        if (isExchanged)
            return

        val currentTop = top.value
        val newTop = Node(x, currentTop)

        if (top.compareAndSet(currentTop, newTop))
            return
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
        val exchangeResult = exchangePop()

        if (exchangeResult != null)
            return exchangeResult

        val currentTop = top.value ?: return null
        val newTop = currentTop.next

        top.compareAndSet(currentTop, newTop)
        return currentTop.x
    }

    private fun exchangePush(x: E): Boolean {
        val eliminationIndex = ThreadLocalRandom.current().nextInt(0, ELIMINATION_ARRAY_SIZE)

        if (eliminationArray[eliminationIndex].compareAndSet(null, x)) {
            for (i in 0..500) {
                if (eliminationArray[eliminationIndex].value == null) {
                    return true
                }
            }

            eliminationArray[eliminationIndex].compareAndSet(x, null)
            return false
        } else return false
    }

    private fun exchangePop(): E? {
        val eliminationIndex = ThreadLocalRandom.current().nextInt(0, ELIMINATION_ARRAY_SIZE)
        val element = eliminationArray[eliminationIndex].value

        if (element != null) {
            eliminationArray[eliminationIndex].compareAndSet(element, null)
            return element as E
        }

        return null
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT