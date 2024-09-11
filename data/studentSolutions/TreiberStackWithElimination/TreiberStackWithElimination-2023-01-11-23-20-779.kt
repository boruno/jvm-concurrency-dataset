package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.concurrent.ThreadLocalRandom

class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<Any?>(ELIMINATION_ARRAY_SIZE)

    private fun random() : Int {
        return ThreadLocalRandom.current().nextInt(ELIMINATION_ARRAY_SIZE)
    }

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        val index = random()
        if (eliminationArray[index].compareAndSet(null, x)) {
            var iterationsWaiting = 1000

            while (iterationsWaiting > 0) {
                if (eliminationArray[index].compareAndSet(null, null)) {
                    return
                }
                iterationsWaiting--
            }
        }

        while (true) {
            val node = Node(x, top.value)
            if (top.compareAndSet(node.next, node)) {
                return
            }
        }
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
        var iterationsSearching = 1000

        while (iterationsSearching > 0) {
            val index = random()
            if (!eliminationArray[index].compareAndSet(null, null)) {
                val eliminationElement = eliminationArray[index].value
                if (eliminationArray[index].compareAndSet(eliminationElement, null)) {
                    return eliminationElement as E?
                } else {
                    break
                }
            }

            iterationsSearching--
        }

        while (true) {

            val node = top.value ?: return null
            if (top.compareAndSet(node, node.next)) {
                return node.x
            }
        }
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT