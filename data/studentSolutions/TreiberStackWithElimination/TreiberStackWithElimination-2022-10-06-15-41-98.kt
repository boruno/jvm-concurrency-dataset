//package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import kotlinx.atomicfu.getAndUpdate
import kotlin.random.Random

class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<Any?>(ELIMINATION_ARRAY_SIZE)

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        while (true) {
            val curTop = top.value;
            val newTop = Node(x, curTop);
            if (top.compareAndSet(curTop, newTop)) return

            val ind = Random.nextInt(ELIMINATION_ARRAY_SIZE)
            if (eliminationArray[ind].compareAndSet(null, x)) return
        }
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
        while (true) {
            val curTop = top.value;
            if (curTop == null) return null
            val newTop = curTop.next
            if (top.compareAndSet(curTop, newTop)) return curTop.x

            val ind = Random.nextInt(ELIMINATION_ARRAY_SIZE)
            val res = eliminationArray[ind].getAndUpdate { null } as E
            if (res != null) return res
        }
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT