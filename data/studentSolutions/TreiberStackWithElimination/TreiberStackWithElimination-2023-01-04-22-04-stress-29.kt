package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.concurrent.ThreadLocalRandom

/**
 * @author : Кулешова Екатерина
 */
class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<E?>(ELIMINATION_ARRAY_SIZE)

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        if (!eliminatePush(x))
            simplePush(x)
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
        return eliminatePop() ?: simplePop()
    }

    private fun simplePush(x: E) {
        var newTop: Node<E>?
        var curTop: Node<E>?

        do {
            curTop = top.value
            newTop = Node(x, curTop)
        } while (!top.compareAndSet(curTop, newTop))
    }

    private fun simplePop(): E? {
        var curTop: Node<E>?

        while (true) {
            curTop = top.value

            return when {
                curTop == null -> null
                top.compareAndSet(curTop, curTop.next) -> curTop.x
                else -> continue
            }
        }
    }

    private fun eliminatePush(x: E): Boolean {
        val random = ThreadLocalRandom.current()
        var pos: Int

        for (i in 0..RETRY_COUNT) {
            pos = random.nextInt(ELIMINATION_ARRAY_SIZE)
            val oldElement = eliminationArray[pos].value
            if (oldElement == null
                && eliminationArray[pos].compareAndSet(oldElement, x)
            ) {
                for (j in 0..WAIT_TIME) {
                    if (eliminationArray[pos].value == null) return true
                }
                eliminationArray[pos].compareAndSet(x, null)
            }

        }
        return false
    }

    private fun eliminatePop(): E? {
        val random = ThreadLocalRandom.current()
        var pos: Int
        var element: E?

        for (i in 0..RETRY_COUNT) {
            pos = random.nextInt(ELIMINATION_ARRAY_SIZE)
            element = eliminationArray[pos].value

            if (element != null
                && eliminationArray[pos].compareAndSet(element, null)
            ) {
                return element
            }
        }
        return null
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT
private const val WAIT_TIME = 100
private const val RETRY_COUNT = ELIMINATION_ARRAY_SIZE