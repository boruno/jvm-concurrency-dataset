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
        println("Start pushing $x")
        while (true) {
            if (tryPush(x)) {
                println("Pushed1 $x")
                return
            }

            exchange(x) ?: return
        }
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
        println("Start popping")
        while (true) {
            val result: ResultHolder<E?>? = tryPop()

            if (result != null) {
                val a = result.value
                println("Popped1 $a")
                return result.value
            }

            val exchangeResult = exchange(null)
            if (exchangeResult != null) {
                println("Popped2 $exchangeResult")
                return exchangeResult as E?
            }
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

    private fun exchange(x: E?): Any? {
        val operationType: OperationType = getOperationTypeFromValue(x)

        while (true) {
            val randomPosition = ThreadLocalRandom.current().nextInt(0, ELIMINATION_ARRAY_SIZE)
            var element = eliminationArray[randomPosition].value

            val maxTime = System.nanoTime() + WAITING_TIMEOUT.inWholeNanoseconds

            when (operationType) {
                OperationType.Push -> {
                    if (eliminationArray[randomPosition].compareAndSet(null, x)) {
                        while (System.nanoTime() < maxTime) {
                            element = eliminationArray[randomPosition].value

                            if (element == null) {
                                println("Pushed2 $x")
                                return null
                            }
                        }

                        if (eliminationArray[randomPosition].compareAndSet(x, null)) {
                            return x
                        }
                    } else {
                        println("Failed to CAS $element with $x")
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