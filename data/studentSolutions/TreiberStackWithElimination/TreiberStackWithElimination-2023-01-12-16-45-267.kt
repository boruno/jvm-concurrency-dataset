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
        val node = Node(x, top.value)
        if (top.compareAndSet(node.next, node)) {
            return
        } else {
            val random = ThreadLocalRandom.current().nextInt(ELIMINATION_ARRAY_SIZE)

            var him = eliminationArray[random].value
            while (!eliminationArray[random].compareAndSet(him, x)) {
                him = eliminationArray[random].value
            }

            if (him != null) {
                while (true) {
                    val node2 = Node(him as E, top.value)
                    if (top.compareAndSet(node2.next, node2)) {
                        break
                    }
                }
            }

            for (i in 0..1000) {
                if (!eliminationArray[random].compareAndSet(x, eliminationArray[random].value)) {
                    return
                }
            }

            if (eliminationArray[random].compareAndSet(x, eliminationArray[random].value)) {
                eliminationArray[random].compareAndSet(x, null)
            }

            while (true) {
                val node2 = Node(x, top.value)
                if (top.compareAndSet(node2.next, node2)) {
                    break
                }
            }
        }
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
        val node = top.value ?: return null
        if (top.compareAndSet(node, node.next)) {
            return node.x
        } else {
            while (true) {
                val random = ThreadLocalRandom.current().nextInt(ELIMINATION_ARRAY_SIZE)

                for (i in 0..ELIMINATION_ARRAY_SIZE) {
                    var him = eliminationArray[(random + i) % ELIMINATION_ARRAY_SIZE].value
                    while (!eliminationArray[(random + i) % ELIMINATION_ARRAY_SIZE].compareAndSet(him, null)) {
                        him = eliminationArray[(random + i) % ELIMINATION_ARRAY_SIZE].value
                    }

                    if (him != null) {
                        return him as E
                    }
                }

                while (true) {
                    val node2 = top.value ?: return null
                    if (top.compareAndSet(node2, node2.next)) {
                        return node2.x
                    }
                }

            }

        }
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT