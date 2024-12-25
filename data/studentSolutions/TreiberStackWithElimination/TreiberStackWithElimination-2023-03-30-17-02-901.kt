//package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.concurrent.ThreadLocalRandom

class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<E?>(ELIMINATION_ARRAY_SIZE)

    private fun tryPerformPush(x: E): Boolean {
        var id = -1

        val randId = ThreadLocalRandom.current().nextInt(eliminationArray.size)
        for (i in randId until randId + eliminationArray.size) {
            val curId = i % eliminationArray.size
            if (eliminationArray[curId].compareAndSet(null, x)) id = curId
        }

        if (id == -1)  return false

        if (eliminationArray[id].compareAndSet(null, null)) {
            return true
        }
        
        return !eliminationArray[id].compareAndSet(x, null)
    }

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        if (tryPerformPush(x)) {
            return
        }

        var newHead: Node<E>?
        var oldHead: Node<E>?
        do {
            oldHead = top.value
            newHead = Node(x, oldHead)
        } while (!top.compareAndSet(oldHead, newHead))
    }

    private fun tryPop(): E? {
        val randId = ThreadLocalRandom.current().nextInt(eliminationArray.size)
        for (i in randId until randId + eliminationArray.size) {
            val curId = i % eliminationArray.size
            for (j in 0 until 1) {
                val result = eliminationArray[curId].getAndSet(null)
                if (result != null) {
                    return result
                }
            }
        }
        return null
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
        val popped = tryPop()
        if (popped != null) {
            return popped
        }

        var newHead: Node<E>?
        var oldHead: Node<E>?
        do {
            oldHead = top.value
            if (oldHead == null) {
                return null
            }
            newHead = oldHead.next
        } while (!top.compareAndSet(oldHead, newHead))
        return oldHead?.x
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT