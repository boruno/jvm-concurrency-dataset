//package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.concurrent.ThreadLocalRandom

class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<E?>(ELIMINATION_ARRAY_SIZE)

    private fun tryTreiberPush(x: E): Boolean {
        val curTop = top.value
        val newTop = Node<E>(x, curTop)
        return top.compareAndSet(curTop, newTop)
    }

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        while (true) {
            // attempt regular push
            if (tryTreiberPush(x)) {
                break
            }
            // the regular push failed -> backoff to elimination array
            val pos = ThreadLocalRandom.current().nextInt(0, ELIMINATION_ARRAY_SIZE)
            val him = eliminationArray[pos]
            if (him.compareAndSet(null, x)) {
                // Do a spin for a few cycles
                for (i in 1..10) {
                    if (him.value != x) {
                        // someone has already taken the value
                        return;
                    }
                }
            }
        }
    }

    private fun tryTreiberPop(): Pair<Boolean, E?> {
        val curTop = top.value
        if (curTop == null) {
            return Pair(true, null)
        }
        val newTop = curTop.next
        return if (top.compareAndSet(curTop, newTop)) {
            Pair(true, curTop.x)
        } else {
            Pair(false, null)
        }
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
        while (true) {
            // attempt a pop from the stack
            val (succ, res) = tryTreiberPop()
            if (succ) {
                return res
            }
            // regular pop failed -> backoff to elimination array
            val pos = ThreadLocalRandom.current().nextInt(0, ELIMINATION_ARRAY_SIZE)
            val him = eliminationArray[pos]
            for (i in 1..10) {
                val old = him.value
                if (him.compareAndSet(old, null)) {
                    return old
                }
            }
        }
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT
