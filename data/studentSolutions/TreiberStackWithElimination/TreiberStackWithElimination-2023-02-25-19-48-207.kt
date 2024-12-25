//package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.concurrent.ThreadLocalRandom

class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<E?>(ELIMINATION_ARRAY_SIZE)

    fun operation_check(op: Int, v: E?, i: Int): Boolean {
        if (op == PUSH_OP) {
            return (eliminationArray[i].compareAndSet(null, v))
        } else {
            return (eliminationArray[i].compareAndSet(v, null) && v != null)
        }
    }

    fun process_el(op: Int, x: E?): Int {
        for (k in (1 .. 5)) {
            val i = ThreadLocalRandom.current().nextInt(ELIMINATION_ARRAY_SIZE)
            var v = x
            if (op == POP_OP) {
                v = eliminationArray[i].value
            }
            if (operation_check(op, v, i)) {
                if (op == POP_OP) {
                    return i
                } else {
                    if (eliminationArray[i].value == null) {
                        return 1
                    }
                    if (!eliminationArray[i].compareAndSet(x, null)) {
                        return 1
                    }
                    break
                }
            }
        }
        return -1
    }

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
//        for (k in (1 .. 5)) {
//            val i = ThreadLocalRandom.current().nextInt(ELIMINATION_ARRAY_SIZE)
//            if (eliminationArray[i].compareAndSet(null, x)) {
//                for (l in (1 .. 1)) {
//                    if (eliminationArray[i].value == null) {
//                        return
//                    }
//                }
//                if (!eliminationArray[i].compareAndSet(x, null))
//                    return
//                break
//            }
//        }
        if (process_el(PUSH_OP, x) == -1) {
            return
        }
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
        val i = process_el(PUSH_OP, null)
        if (i != -1) {
//            val i = ThreadLocalRandom.current().nextInt(ELIMINATION_ARRAY_SIZE)
            return eliminationArray[i].value
        }
//        for (k in (1 .. 5)) {
//            val i = ThreadLocalRandom.current().nextInt(ELIMINATION_ARRAY_SIZE)
//            val value = eliminationArray[i].value
//            if (value != null &&
//                eliminationArray[i].compareAndSet(value, null)) {
//                return value
//            }
//        }
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
private const val PUSH_OP = 1
private const val POP_OP = 0