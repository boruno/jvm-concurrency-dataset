//package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.lang.management.ThreadInfo
import java.util.concurrent.ThreadLocalRandom

class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<Any?>(ELIMINATION_ARRAY_SIZE)

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        val r = ThreadLocalRandom.current().nextInt()
        // println("push $x $r")
        val node = Node(x, top.value)
        if (top.compareAndSet(node.next, node)) {
            return
        } else {
            val random = ThreadLocalRandom.current().nextInt(ELIMINATION_ARRAY_SIZE)

            // println("push! ${node.x} $r")

            val prevX = eliminationArray[random % ELIMINATION_ARRAY_SIZE].getAndSet(x) as E?

            if (prevX != null) {
                while (true) {
                    val nodePrev = Node(prevX, top.value)
                    if (top.compareAndSet(nodePrev.next, nodePrev)) {
                        break
                    }
                }
            }

            for (i in 0..1000) {
                if (!eliminationArray[random % ELIMINATION_ARRAY_SIZE].compareAndSet(x, x)) {
                    return
                }
            }

            if (eliminationArray[random % ELIMINATION_ARRAY_SIZE].compareAndSet(x, null)) {
                while (true) {
                    val nodeInsert = Node(x, top.value)
                    if (top.compareAndSet(nodeInsert.next, nodeInsert)) {
                        return
                    }
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
        while (true) {
            val random = ThreadLocalRandom.current().nextInt(ELIMINATION_ARRAY_SIZE)

            for (i in 0 until ELIMINATION_ARRAY_SIZE) {
                var him = eliminationArray[(random + i) % ELIMINATION_ARRAY_SIZE].value

                if (him != null) {
                    if (eliminationArray[(random + i) % ELIMINATION_ARRAY_SIZE].compareAndSet(him, null)) {
                        return (him as E)
                    }
                }
            }

            while (true) {
                val node = top.value ?: return null
                if (top.compareAndSet(node, node.next)) {
                    return node.x
                }
            }
        }
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT