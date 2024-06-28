package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*

class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<Any?>(ELIMINATION_ARRAY_SIZE)
    private val DONE = Int.MIN_VALUE + 1
    private val R = Random()


    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        if (x == DONE || x == null) {
            classicPush(x)
            return
        }
        val i = R.nextInt(ELIMINATION_ARRAY_SIZE)
        if (eliminationArray[i].compareAndSet(null, x)) {
            for (counter in 0..99) {
                if (eliminationArray[i].compareAndSet(DONE, null)) return
            }
            while (true) {
                if (eliminationArray[i].compareAndSet(x, null)) {
                    classicPush(x)
                    break
                } else if (eliminationArray[i].compareAndSet(DONE, null)) {
                    break
                }
            }
        }
    }

    fun classicPush(x: E) {
        var new: Node<E>
        var old : Node<E>?
        do {
            old = top.value
            new = Node(x, old)
        } while (!top.compareAndSet(old, new))
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
        val i = R.nextInt(ELIMINATION_ARRAY_SIZE)
        val value = eliminationArray[i].value
        if (value == null || value == DONE) {
            return classicPop()
        }
        return if (eliminationArray[i].compareAndSet(value, DONE)) {
            value as E?
        } else {
            classicPop()
        }
    }

    fun classicPop(): E? {
        var oldHead: Node<E>?
        var newHead: Node<E>?
        do {
            oldHead = top.value
            if (oldHead == null) {
                return null
            }
            newHead = oldHead.next
        } while (!top.compareAndSet(oldHead, newHead))
        return oldHead?.x
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT