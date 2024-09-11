package mpp.stackWithElimination

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

            if (exchange(x) == null)
                return
            else forcePush(x)
        }
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
        while (true) {
            val result: ResultHolder<E?>? = tryPop()

            if (result != null)
                return result.value

            val exchangeResult = exchange(null)

            if (exchangeResult != null)
                return exchangeResult as E?

            return forcePop()
        }
    }

    private fun tryPush(x: E): Boolean {
        val currentTop = top.value
        val newTop = Node(x, currentTop)

        return top.compareAndSet(currentTop, newTop)
    }

    private fun tryPop(): ResultHolder<E?>? {
        val currentTop = top.value ?: return ResultHolder(null)
        val newTop = currentTop.next

        if (top.compareAndSet(currentTop, newTop)) {
            return ResultHolder(currentTop.x)
        }

        return null
    }

    private fun forcePush(x: E) {
        while (true) {
            val currentTop = top.value
            val newTop = Node(x, currentTop)

            if (top.compareAndSet(currentTop, newTop))
                return
        }
    }

    private fun forcePop(): E? {
        while (true) {
            val currentTop = top.value ?: return null
            val newTop = currentTop.next

            if (top.compareAndSet(currentTop, newTop))
                return currentTop.x
        }
    }

    private fun exchange(x: E?): Any? {
        val operationType: OperationType = getOperationTypeFromValue(x)

        while (true) {
            val randomPosition = ThreadLocalRandom.current().nextInt(0, ELIMINATION_ARRAY_SIZE)
            var element = eliminationArray[randomPosition].value

            val maxTime = System.nanoTime() + WAITING_TIMEOUT.inWholeNanoseconds

            when (operationType) {
                OperationType.Push -> {
                    if (eliminationArray[randomPosition].compareAndSet(null, x)) {
                        Thread.sleep(10)

                        element = eliminationArray[randomPosition].value

                        if (element == null)
                            return null
                        else {
                            eliminationArray[randomPosition].value = null
                            return element
                        }
                    }
                }
                OperationType.Pop -> {
                    if (element != null && eliminationArray[randomPosition].compareAndSet(element, null)) {
                        return element
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

private val WAITING_TIMEOUT: Duration = 10.toDuration(unit = DurationUnit.NANOSECONDS)

private enum class OperationType {
    Push,
    Pop,
}

private class ResultHolder<T>(val value: T)