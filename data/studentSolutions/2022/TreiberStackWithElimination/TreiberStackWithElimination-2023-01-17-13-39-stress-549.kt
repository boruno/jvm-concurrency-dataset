package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*

class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<E?>(ELIMINATION_ARRAY_SIZE)
    private val rnd = Random()


    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        if (writeToEliminationArray(x)) {
            return
        }

        while (true) {
            val nodeToPush = Node(x, top.value)

            if (top.compareAndSet(nodeToPush.next, nodeToPush)) {
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
        val readValue = readFromEliminationArray();

        if (readValue != null) {
            return readValue
        } else {
            while (true) {
                val topNode = top.value ?: return null

                if (top.compareAndSet(topNode, topNode.next)) {
                    return topNode.x
                }
            }
        }
    }

    private fun writeToEliminationArray(x: E): Boolean {
        val index = rnd.nextInt(ELIMINATION_ARRAY_SIZE)
        val refArrValue = eliminationArray[index]

        if (refArrValue.compareAndSet(null, x)) {
            for (i in 0..ELIMINATION_WAITING_STEPS) {
                if (refArrValue.compareAndSet(null, null)) {
                    return true
                }
            }
            return !refArrValue.compareAndSet(x, null)
        }
        return false
    }

    private fun readFromEliminationArray(): E? {
        val index = rnd.nextInt(ELIMINATION_ARRAY_SIZE)
        val refArrValue = eliminationArray[index]

        for (i in 0..ELIMINATION_WAITING_STEPS) {
            val arrValue = refArrValue.getAndSet(null)
            if (arrValue != null) {
                return arrValue
            }
        }

        return null
    }

}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT

private const val ELIMINATION_WAITING_STEPS = 100