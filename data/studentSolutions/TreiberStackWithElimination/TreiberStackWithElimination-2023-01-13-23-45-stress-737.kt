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
            if (eliminationArray[i].compareAndSet(DONE, null)) {
                return
            }
            while (true) {
                if (eliminationArray[i].compareAndSet(x, null)) {
                    classicPush(x)
                    break
                } else if (eliminationArray[i].compareAndSet(DONE, null)) {
                    break
                }
            }
        } else {
            classicPush(x)
        }
    }

    private fun classicPush(x: E) {
        while (true) {
            val oldHead = top.value
            val newHead = Node(x, oldHead)
            if (top.compareAndSet(oldHead, newHead)) {
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

    private fun classicPop(): E? {
        while (true) {
            val oldHead = top.value ?: return null
            val newHead = oldHead.next
            if (top.compareAndSet(oldHead, newHead)) {
                return oldHead.x
            }
        }
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT