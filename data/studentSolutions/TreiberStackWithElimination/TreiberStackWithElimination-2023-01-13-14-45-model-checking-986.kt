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

        if (tryEliminationPush(x)) {
            return
        }

        while (true) {
            val curTop = top.value
            val newTop = Node(x, curTop)
            if (top.compareAndSet(curTop, newTop)) return
        }
    }

    private fun tryEliminationPush(x: E): Boolean {
        val randInd = ThreadLocalRandom.current().nextInt(ELIMINATION_ARRAY_SIZE)
        var resInd = -1

        for (i in 0 until ELIMINATION_ARRAY_SIZE) {
            val ind = (randInd + i) % ELIMINATION_ARRAY_SIZE
            if (eliminationArray[ind].compareAndSet(null, x)) {
                resInd = ind
                break
            }
        }

        if (resInd == -1) {
            return false
        }

        for (i in 0 until ELIMINATION_BACKOFF) {
            if (eliminationArray[resInd].compareAndSet(null, null)) {
                return true
            }
        }

        return !eliminationArray[resInd].compareAndSet(x, null)
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
        val res = tryEliminationPop()
        if (res != null) {
            return res
        }

        while (true) {
            val curTop = top.value ?: return null
            val newTop = curTop.next
            if (top.compareAndSet(curTop, newTop)) return curTop.x
        }
    }

    private fun tryEliminationPop(): E? {
        val randInd = ThreadLocalRandom.current().nextInt(ELIMINATION_ARRAY_SIZE)

        for (i in 0 until ELIMINATION_ARRAY_SIZE) {
            val ind = (randInd + i) % ELIMINATION_ARRAY_SIZE
            for (j in 0 until ELIMINATION_BACKOFF) {
                val res = eliminationArray[ind].getAndSet(null)
                if (res != null) {
                    return res
                }
            }
        }

        return null
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT
private const val ELIMINATION_BACKOFF = 10