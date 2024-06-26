package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.rmi.UnexpectedException

val DONE = Any()
class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<Any?>(ELIMINATION_ARRAY_SIZE)


    fun pushStack(x: E) {
        while (true) {
            val newTop = Node(x, top.value)
            if (top.compareAndSet(newTop.next, newTop)) {
                return
            }
        }
    }

    fun popStack(): E? {
        while (true) {
            val x = top.value ?: return null
            if (top.compareAndSet(x, x.next)) {
                return x.x
            }
        }
    }

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        for (i in 0..4) {
            val index = i % ELIMINATION_ARRAY_SIZE
            if (eliminationArray[index].compareAndSet(null, x)) {
                for (i in 0..1024) {
                    if (eliminationArray[index].compareAndSet(DONE, null)) {
                        return
                    }
                }
                if (eliminationArray[index].compareAndSet(x, null)) {
                    pushStack(x)
                    return
                }
                if (!eliminationArray[index].compareAndSet(DONE, null)) {
                    throw RuntimeException("")
                }
            }
        }
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
        for (i in 0..4) {
            val index = i % ELIMINATION_ARRAY_SIZE
            val value = eliminationArray[index].value ?: continue
            if (value == DONE) { continue }
            if (eliminationArray[index].compareAndSet(value, DONE)) {
                return value as E
            }
        }
        return popStack()
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT