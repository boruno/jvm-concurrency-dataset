package mpp.stackWithElimination

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
        val elem = x
        val ind1 = (0..ELIMINATION_ARRAY_SIZE).random()
        for (del in 0..3) {
            val sum = ind1 + del
            val nIndex = sum % ELIMINATION_ARRAY_SIZE
            if (eliminationArray[nIndex].compareAndSet(null, elem)) {
                if (!eliminationArray[nIndex].compareAndSet(elem, null)) {
                    return
                }
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
        val ind1 = (0..ELIMINATION_ARRAY_SIZE).random()
        for (indDel in 0..3) {
            val sum = (ind1 + indDel)
            val nIndex = sum % ELIMINATION_ARRAY_SIZE
            val x = eliminationArray[nIndex].value
            if (x != null && eliminationArray[nIndex].compareAndSet(x, null)) {
                return x as E?
            }
        }

        if (top.value == null){
            return null
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