package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*
import java.util.concurrent.ThreadLocalRandom
import kotlin.random.Random

/**
 * @author Pologov Nikita
 */

class TreiberStackWithElimination<E> {
    private val SIZE = 15
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<Any?>(SIZE)

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        val randomIndex = getRandomIndex();
        val leftBound = getLeftBound(randomIndex);
        val rightBound = getRightBound(randomIndex);
        while (true) {
            val head = top.value
            if (head == null) {
                throw EmptyStackException()
            }
            val newHead = Node(x, head)
            if (top.compareAndSet(head, newHead)) {
                break
            }
        }
    }

    private fun getRightBound(randomIndex: Any): Any {
        TODO("Not yet implemented")
    }

    private fun getLeftBound(randomIndex: Any): Any {
        TODO("Not yet implemented")
    }

    private fun getRandomIndex(): Any {
        return ThreadLocalRandom.current().nextInt(SIZE);
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
        while (true) {
            val head = top.value
            if (head == null) {
                return null
            }
            if (top.compareAndSet(head, head.next)) {
                return head.x
            }
        }
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT