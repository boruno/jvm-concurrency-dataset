package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.concurrent.ThreadLocalRandom

class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<E?>(ELIMINATION_ARRAY_SIZE)

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        val idx = ThreadLocalRandom.current().nextInt(eliminationArray.size)
        for (i in idx until idx + eliminationArray.size) {
            val curId = i % eliminationArray.size
            if (eliminationArray[curId].compareAndSet(null, x)) {
                if (eliminationArray[curId].compareAndSet(null, null)) return
                else if (!eliminationArray[curId].compareAndSet(x, null)) return
                while (true) {
                    val head: Node<E>? = top.value
                    val newHead = Node(x, head)
                    if (top.compareAndSet(head, newHead)) return
                }
            }
        }
    }

    private fun tryPerformPop(): E? {
        val randId = ThreadLocalRandom.current().nextInt(eliminationArray.size)
        for (i in randId until randId + eliminationArray.size) {
            val curId = i % eliminationArray.size
            val result = eliminationArray[curId].getAndSet(null)
            if (result != null) return result
        }
        return null
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
//        val randId = ThreadLocalRandom.current().nextInt(eliminationArray.size)
//        for (i in randId until randId + eliminationArray.size) {
//            val curId = i % eliminationArray.size
//            val result = eliminationArray[curId].getAndSet(null)
//            if (result != null) return result
//        }
        val p = tryPerformPop()
        if (p != null) return p

        while (true) {
            val head: Node<E> = top.value ?: return null
            if (top.compareAndSet(head, head.next)) return head.x
        }
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT