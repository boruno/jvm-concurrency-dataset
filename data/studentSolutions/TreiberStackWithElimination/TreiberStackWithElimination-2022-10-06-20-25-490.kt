package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.EmptyStackException

class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<Any?>(ELIMINATION_ARRAY_SIZE)

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        if (eliminationArray[0].compareAndSet(null, x)) {
            return
        }
        if (eliminationArray[1].compareAndSet(null, x)) {
            return
        }
        val x2 = eliminationArray[1].getAndSet(null)
        val x1 = eliminationArray[0].getAndSet(x2)
        forcePush(x1 as E);
    }
    private fun forcePush(x: E) {
        while (true) {
            val curTop = top.value
            val newNode = Node<E>(x, curTop)
            if (top.compareAndSet(curTop, newNode)) {
                break;
            }
        }
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
        val x = eliminationArray[1].getAndSet(null)
        if (x != null) {
            return x as E
        }
        val y = eliminationArray[0].getAndSet(null)
        if (y != null) {
            return y as E
        }
        return forcePop()
    }
    private fun forcePop(): E?{
        while(true) {
            val curTop = top.value ?: throw EmptyStackException()
            val newTop = curTop.next
            if (top.compareAndSet(curTop, newTop)){
                return curTop.x
            }
        }
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT