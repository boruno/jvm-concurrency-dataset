//package mpp.stack

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import kotlin.random.Random

class TreiberStack<E> {
    private val top = atomic<Node<E>?>(null)
    private val elimination = atomicArrayOfNulls<E?>(ELIMINATION_ARRAY_SIZE)

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        val index = Random.nextInt() % ELIMINATION_ARRAY_SIZE
        val place = elimination[index]
        if (place.compareAndSet(null, x)) {
            for (i in 1 .. 4) {
                if (place.compareAndSet(null, null)) {
                    return
                }
            }
            if (!place.compareAndSet(x, null)) {
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
        val index = Random.nextInt() % ELIMINATION_ARRAY_SIZE
        val place = elimination[index]
        for (i in 1 .. 4) {
            val elementRef = place.value
            if (place.compareAndSet(elementRef, null)) {
                return elementRef
            }
        }

        while (true) {
            val curTop = top.value ?: return null
            val newTop = curTop.next
            if (top.compareAndSet(curTop, newTop)) {
                return curTop.x
            }
        }
    }
}

private class Node<E>(val x: E, val next: Node<E>?)


private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT

