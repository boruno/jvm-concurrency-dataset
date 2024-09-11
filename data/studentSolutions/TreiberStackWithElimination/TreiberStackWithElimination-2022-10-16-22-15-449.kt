package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.concurrent.ThreadLocalRandom
import kotlin.random.Random
import kotlin.time.*

class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<Any?>(ELIMINATION_ARRAY_SIZE)

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        if (tryPush(x))
            return

        exchange(x) ?: return // Exchange with pop() was performed if result is null
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
        while (true) {
            val element = tryPop()

            if (element != null)
                return element

            val exchangeResult = exchange(null)
            if (exchangeResult != null)
                return exchangeResult as E?
        }
    }

    private fun tryPush(x: E): Boolean {
        val currentTop = top.value
        val newTop = Node(x, currentTop)

        return top.compareAndSet(currentTop, newTop)
    }

    private fun tryPop(): E? {
        val currentTop = top.value ?: return null
        val newTop = currentTop.next

        if (top.compareAndSet(currentTop, newTop))
            return currentTop.x

        return null
    }

    private fun exchange(x: E?): Any? {
        while (true) {
            val randomPosition = Random.nextInt(0, ELIMINATION_ARRAY_SIZE)
            var element = eliminationArray[randomPosition].value

            val maxTime = System.nanoTime() + WAITING_TIMEOUT.inWholeNanoseconds

            if (element == null) {
                if (eliminationArray[randomPosition].compareAndSet(null, x)) {
                    while (System.nanoTime() < maxTime) {
                        element = eliminationArray[randomPosition].value
                        if (element != null) {
                            eliminationArray[randomPosition].value = null
                            return element
                        }
                    }
                }
            } else if (eliminationArray[randomPosition].compareAndSet(element, x)) {
                return element
            }
        }
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT

private val WAITING_TIMEOUT: Duration = 1000.toDuration(unit = DurationUnit.NANOSECONDS)