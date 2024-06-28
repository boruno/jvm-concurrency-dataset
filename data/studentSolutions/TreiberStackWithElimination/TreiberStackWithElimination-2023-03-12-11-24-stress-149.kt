package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.concurrent.ThreadLocalRandom


class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<Any?>(ELIMINATION_ARRAY_SIZE)
    private val done: String = "DONE"
    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        val randInd = ThreadLocalRandom.current().nextInt(ELIMINATION_ARRAY_SIZE)
        var found = Integer.MAX_VALUE
        for (i in 0 until ELIMINATION_ARRAY_SIZE) {
            val index = (randInd + i) % ELIMINATION_ARRAY_SIZE
            if (eliminationArray[index].compareAndSet(null, x)) {
                found = index
                break
            }
        }
        if (found != Int.MAX_VALUE) {
            for (i in 0 until ELIMINATION_ARRAY_SIZE) {
                if (eliminationArray[found].compareAndSet(done, null)) {
                    return
                }
            }
            if (eliminationArray[found].compareAndSet(x,null)) {
                pushPrime(x)
            }
        } else {
            pushPrime(x)
        }
    }

    private fun pushPrime(x: E) {
        while (true) {
            val curHead = top.value
            val newHead = Node(x, curHead)
            if (top.compareAndSet(curHead, newHead)) return
        }
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    @Suppress("UNCHECKED_CAST")
    fun pop(): E? {
        val randInd = ThreadLocalRandom.current().nextInt(ELIMINATION_ARRAY_SIZE)

        for (i in 0 until ELIMINATION_ARRAY_SIZE) {
            val index = (randInd + i) % ELIMINATION_ARRAY_SIZE
            val node = eliminationArray[index].value
            if (node != null && eliminationArray[index].compareAndSet(node, null)) {
                return node as E
            }
        }

        return popPrime()
    }

    private fun popPrime(): E? {
        while (true) {
            val curHead = top.value ?: return null
            val newHead = curHead.next
            if (top.compareAndSet(curHead, newHead)) {
                return curHead.x
            }
        }
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT