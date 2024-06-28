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
        var newTop: Node<E>?
        var curTop: Node<E>?

        while (true) {
            if (eliminatePush(x)) return

            curTop = top.value
            newTop = Node(x, curTop)
            if (top.compareAndSet(curTop, newTop)) return
        }
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
        var curTop: Node<E>?
        var element: E?

        while (true) {
            element = eliminatePop()
            if (element != null) return element

            curTop = top.value
            return when {
                curTop == null -> null
                top.compareAndSet(curTop, curTop.next) -> curTop.x
                else -> continue
            }
        }
    }

    private fun eliminatePush(x: E): Boolean {
        val pos = ThreadLocalRandom.current().nextInt(ELIMINATION_ARRAY_SIZE)
        val oldElement = eliminationArray[pos].value

        if (oldElement == null
            && eliminationArray[pos].compareAndSet(oldElement, x)
        ) {
            for (i in 0..WAIT_TIME) {
                if (eliminationArray[pos].value == null) return true
            }
            eliminationArray[pos].compareAndSet(x, null)
        }
        return false
    }

    private fun eliminatePop(): E? {
        val pos = ThreadLocalRandom.current().nextInt(ELIMINATION_ARRAY_SIZE)
        val element = eliminationArray[pos].value

        return if (element != null
            && eliminationArray[pos].compareAndSet(element, null)
        ) {
            element
        } else {
            null
        }
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT
private const val WAIT_TIME = 100