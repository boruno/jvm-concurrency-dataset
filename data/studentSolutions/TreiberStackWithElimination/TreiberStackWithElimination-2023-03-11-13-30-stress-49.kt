package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import kotlin.random.Random


class TreiberStackWithElimination<E> {
    private val random: Random = Random(0)
    private val ELIMINATION_SIZE = 32
    private val WINDOW_SIZE = 4
    private val CNT_NOP = 100
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<Any?>(ELIMINATION_ARRAY_SIZE)

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        val right = 1
        val left = 0
        val CNT_PASS = 2
        val nodeToPush = Node<Any?>(x, null)
        for (num in left..right) {
            if (!eliminationArray[num].compareAndSet(null, nodeToPush)) {
                continue
            }
            var ptr = 0
            while (ptr < CNT_PASS) {
                val curNode = eliminationArray[num].value
                if (curNode == null) {
                    eliminationArray[num].value = null
                    return
                }
                ptr++
            }
            if (!eliminationArray[num].compareAndSet(nodeToPush, null)) {
                eliminationArray[num].value = null
                return
            } else {
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
        val right = 1
        val left = 0

        val newElem: Node<*> = Node<Any?>(Int.MAX_VALUE, null)
        for (num in left..right) {
            val elem = eliminationArray[num].value
            if (elem != null && elem != Int.MAX_VALUE) {
                if (eliminationArray[num].compareAndSet(elem, newElem)) {
                    return elem as E?
                }
            }
        }
        while (true) {
            val current = top.value
            if (current != null) {
                if (top.compareAndSet(current, current.next)) {
                    return current.x
                }
            } else {
                return null
            }
        }
    }

}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT