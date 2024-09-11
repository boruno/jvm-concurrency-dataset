package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*

class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<Any?>(ELIMINATION_ARRAY_SIZE)
    private val random = Random(0)
    private val waitTime = 100
    private val windowSize = 4

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        if (tryPushWithElimination(x)) {
            val newHead = Node(x, null)
            var oldHead: Node<E>?

            do {
                oldHead = top.value
                newHead.next = oldHead
            } while (!top.compareAndSet(oldHead, newHead))
        }
    }

    private fun tryPushWithElimination(x: E): Boolean {
        val ind = random.nextInt(ELIMINATION_ARRAY_SIZE)
        for (del in 0 until windowSize) {
            val i = (ind + del) % ELIMINATION_ARRAY_SIZE
            if (eliminationArray[i].compareAndSet(null, x)) {
                var cnt: Int = waitTime

                while (cnt-- > 0) {
                    assert(true)
                }

                return !eliminationArray[i].compareAndSet(x, null)
            }
        }

        return false
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
        var oldHead: Node<E>?
        var newHead: Node<E>?

        do {
            oldHead = top.value

            if (oldHead == null)
                return null

            newHead = oldHead.next
        } while (!top.compareAndSet(oldHead, newHead))
        return oldHead!!.x
    }
}

private class Node<E>(val x: E, var next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT