//package mpp.stackWithElimination

import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls

class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<Any?>(ELIMINATION_ARRAY_SIZE)

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        var idx : Int? = null
        var k = 0
        while (k < 100) {
            //println(x)
            if (eliminationArray[0].value == null) {
                if (eliminationArray[0].compareAndSet(null, x)) {
                    idx = 0
                    break
                }
            }
            if (eliminationArray[1].value == null) {
                if (eliminationArray[1].compareAndSet(null, x)) {
                    idx = 1
                    break
                }
            }
            k++
        }
        var ch = 0
        while (ch < 3 && idx != null) {
            if (eliminationArray[idx].value == null) {
                return
            }
            ch++
        }
        if (idx != null) {
            if (!eliminationArray[idx].compareAndSet(x, null)) {
                return
            }
        }
        while (true) {
            val head = top.value
            val newHead = Node(x, head)
            if (top.compareAndSet(head, newHead)) {
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
        val task = eliminationArray[0].value
        if (task != null) {
            if (eliminationArray[0].compareAndSet(task, null)) {
                return task as E?
            }
        }
        val task1 = eliminationArray[1].value
        if (task1 != null) {
            if (eliminationArray[1].compareAndSet(task1, null)) {
                return task1 as E?
            }
        }
        while(true) {
            val head = top.value
            if (head == null) {
                return null
            }
            val next = head.next
            if (top.compareAndSet(head, next)) {
                return head.x
            }
        }
    }
}

private class Node<E>(val x: E, val next: Node<E>?)


private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT