//package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.concurrent.ThreadLocalRandom
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

            exchange(x) ?: return
        }
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
        while (true) {
            val result = tryPop()

            if (result.size != 0)
                return result[0]

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

    private fun tryPop(): ArrayList<E?> {
        val resultHolder: ArrayList<E?> = ArrayList(1)

        val currentTop = top.value ?: return resultHolder
        val newTop = currentTop.next

        if (top.compareAndSet(currentTop, newTop)) {
            resultHolder[0] = currentTop.x
            return resultHolder
        }

        return ArrayList()
    }

    private fun exchange(x: E?): Any? {
        while (true) {
            val operationType = getOperationTypeFromValue(x)

            val randomPosition = ThreadLocalRandom.current().nextInt(0, ELIMINATION_ARRAY_SIZE)
            var element = eliminationArray[randomPosition].value

            val maxTime = System.nanoTime() + WAITING_TIMEOUT.inWholeNanoseconds

            when (operationType) {
                OperationType.Push -> {
                    if (element == null) {
                        if (eliminationArray[randomPosition].compareAndSet(null, x)) {
                            while (System.nanoTime() < maxTime) {
                                element = eliminationArray[randomPosition].value

                                if (element == null) {
                                    return null
                                }
                            }

                            if (eliminationArray[randomPosition].compareAndSet(x, null)) {
                                val currentTop = top.value
                                val newTop = Node(x!!, currentTop)

                                top.compareAndSet(currentTop, newTop)
                                return null
                            }
                        }
                    }
                }
                OperationType.Pop -> {
                    if (element == null) {
                        while (System.nanoTime() < maxTime) {
                            element = eliminationArray[randomPosition].value

                            if (element != null && eliminationArray[randomPosition].compareAndSet(element, null)) {
                                return element
                            }
                        }
                    } else {
                        return if (eliminationArray[randomPosition].compareAndSet(element, null)) {
                            element
                        } else null
                    }
                }
            }
        }
    }

    private fun getOperationTypeFromValue(x: E?): OperationType {
        if (x == null)
            return OperationType.Pop

        return OperationType.Push
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT

private val WAITING_TIMEOUT: Duration = 100.toDuration(unit = DurationUnit.NANOSECONDS)

private enum class OperationType {
    Push,
    Pop,
}