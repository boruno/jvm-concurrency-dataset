//package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.lang.Integer.max
import java.lang.Integer.min
import java.util.concurrent.ThreadLocalRandom

class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<E?>(ELIMINATION_ARRAY_SIZE)

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        val ind = 0
        val from = max(0, ind - PUSH_TRIES / 2)
        val to = min(ELIMINATION_ARRAY_SIZE - 1, ind + PUSH_TRIES / 2)
        for (i in (from..to)) {
            if (eliminationArray[i].compareAndSet(null, x)) {
                for (j in (1 .. PUSH_WAIT_ITERATIONS)) {
                    if (eliminationArray[i].value == null) {
                        return
                    }
                }
                if (!eliminationArray[i].compareAndSet(x, null))
                    return
                break
            }
        }

//        for (i in (1 .. PUSH_TRIES)) {
//            val ind = ThreadLocalRandom.current().nextInt(ELIMINATION_ARRAY_SIZE)
//            if (eliminationArray[ind].compareAndSet(null, x)) {
//                continue
//            }
//            for (j in (1 .. PUSH_WAIT_ITERATIONS)) {
//                if (eliminationArray[ind].value == null) {
//                    return
//                }
//            }
//            eliminationArray[ind].compareAndSet(x, null)
//            break
//
//        }


        while (true) {
            val prev = top.value
            val cur = Node(x, prev)
            if (top.compareAndSet(prev, cur)) {
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
        for (i in (0..1)) {
            val ind = ThreadLocalRandom.current().nextInt(ELIMINATION_ARRAY_SIZE)
            val value = eliminationArray[i].value
            if (eliminationArray[i].value == null && eliminationArray[i].compareAndSet(value, null)) {
                return value
            }
        }

        while (true) {
            val prev = top.value ?: return null
            val cur = prev.next
            if (top.compareAndSet(prev, cur)) {
                return prev.x
            }
        }
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT
private const val PUSH_TRIES = 2
private const val PUSH_WAIT_ITERATIONS = 1
private const val POP_TRIES = 10