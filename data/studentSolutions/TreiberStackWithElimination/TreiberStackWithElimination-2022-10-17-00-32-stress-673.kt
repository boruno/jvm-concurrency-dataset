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
        while (true) {
            if (tryPush(x))
                return

            exchange(x) ?: return // Exchange with pop() was performed if result is null
        }
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
                return exchangeResult as E? // Exchange with non-empty cell was performed if result is not null
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
            val randomPosition = ThreadLocalRandom.current().nextInt(0, ELIMINATION_ARRAY_SIZE)
            var element = eliminationArray[randomPosition].value

            val maxTime = System.nanoTime() + WAITING_TIMEOUT.inWholeNanoseconds

            // If we came here from push() operation and found out empty cell.
            if (element == null && x != null) {
                // Null stands for 'element' here.
                // If CAS returned true that means current thread has successfully found a place for exchange.
                // If CAS failed here that means another thread succeeded with taking this cell, so continue.
                if (eliminationArray[randomPosition].compareAndSet(null, x)) {
                    // While timeout is not elapsed wait for
                    while (System.nanoTime() < maxTime) {
                        element = eliminationArray[randomPosition].value

                        // If some thread takes the value before timeout reached, the exchange is completed.
                        if (element == null) {
                            return null
                        }
                    }

                    // Just pop() the value if timeout reached.
                    if (!eliminationArray[randomPosition].compareAndSet(x, null)) {
                        element = eliminationArray[randomPosition].value
                        eliminationArray[randomPosition].value = null
                        return element
                    }
                }
            } else if (element != null && x != null) {
                if (!eliminationArray[randomPosition].compareAndSet(null, x)) {
                    while (System.nanoTime() < maxTime) {
                        element = eliminationArray[randomPosition].value

                        return if (element == null && eliminationArray[randomPosition].compareAndSet(null, x)) {
                            null
                        } else x
                    }
                }
            } else if (element != null && x == null) {
                return if (eliminationArray[randomPosition].compareAndSet(element, null)) {
                    element
                } else null
            }
        }
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT

private val WAITING_TIMEOUT: Duration = 1000.toDuration(unit = DurationUnit.NANOSECONDS)