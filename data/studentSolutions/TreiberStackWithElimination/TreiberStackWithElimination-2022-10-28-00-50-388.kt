//package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls

class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<Any?>(ELIMINATION_ARRAY_SIZE)

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        for (i in 0..maxIterations) {
            val index = i % ELIMINATION_ARRAY_SIZE
            if (eliminationArray[index].compareAndSet(null, x)) {
                for (j in 0..maxIterations) {
                    if (eliminationArray[index].compareAndSet(DONE(), null)) {
                        return
                    }
                }
                if (eliminationArray[index].compareAndSet(x, null)) {
                    break
                }
                return
            }
        }

        while (true) {
            val curTop = top.value
            val newTop = Node(x, curTop)
            if (top.compareAndSet(curTop, newTop)) {
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
        for (i in 0..maxIterations) {
            val index = i % ELIMINATION_ARRAY_SIZE
            val x = eliminationArray[index].value
            if (x != null && x != DONE() && eliminationArray[index].compareAndSet(x, DONE())) {
                return x as? E
            }
        }

        while (true) {
            val curTop = top.value
            val newTop = curTop?.next
            if (top.compareAndSet(curTop, newTop)) {
                return curTop?.x
            }
        }
    }
}

private class Node<E>(val x: E, val next: Node<E>?)
private class DONE

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT
private const val maxIterations = 10