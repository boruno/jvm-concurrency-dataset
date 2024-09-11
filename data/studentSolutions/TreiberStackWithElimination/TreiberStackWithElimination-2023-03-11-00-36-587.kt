package mpp.stackWithElimination

import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import kotlin.random.Random


class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<Any?>(ELIMINATION_ARRAY_SIZE)

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        val rand: Int = Random.Default.nextInt(15)
        val SEARCHING_RANGE = 5
        val ELIMINATION_SIZE = 15
        val SPIN_WAIT = 50
        for (i in Math.max(0, rand - SEARCHING_RANGE) until Math.min(ELIMINATION_SIZE, rand + SEARCHING_RANGE)) {
            val el: AtomicRef<Any?> = eliminationArray.get(i)
            val X = x
            if (el.compareAndSet(null, X)) {
                for (k in 0 until SPIN_WAIT) {
                    if (el.value == null) return
                }
                if (!el.compareAndSet(X, null)) return
                break
            }
        }
        while (true){
            var head = top.value
            var head1 = Node(x, head)
            if (top.compareAndSet(head, head1)) {
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
        if (top.value == null){
            return null
        }
        val SEARCHING_RANGE = 5
        val ELIMINATION_SIZE = 15
        val SPIN_WAIT = 50
        val rand = Random.nextInt(ELIMINATION_SIZE)

        for (i in Math.max(0, rand - SEARCHING_RANGE) until Math.min(ELIMINATION_SIZE, rand + SEARCHING_RANGE)) {
            val el: AtomicRef<Any?> = eliminationArray.get(i)
            val value = el.value
            if (value != null) {
                if (el.compareAndSet(value, null)) {
                    return value as E?
                }
            }
        }
        while (true) {
            var head = top.value
            if (head != null) {
                if (top.compareAndSet(head, head.next)) {
                    return head.x
                }
            }
            else return null
        }
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT