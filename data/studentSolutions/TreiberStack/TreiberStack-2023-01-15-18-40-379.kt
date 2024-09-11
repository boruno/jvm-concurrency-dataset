package mpp.stack

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import kotlin.random.Random

class TreiberStack<E> {
    private val top = atomic<Node<E>?>(null)
    private val elimination = atomicArrayOfNulls<E?>(ELIMINATION_ARRAY_SIZE)
    private val elimRand = Random(123)

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        val index = elimRand.nextInt(ELIMINATION_ARRAY_SIZE)
        for (i in 1..4) {
            if (elimination[index].compareAndSet(null, x)) {
                for (j in 1..4) {
                    if (elimination[index].compareAndSet(null, null)) {
                        return
                    }
                }
                if (!elimination[index].compareAndSet(x, null)) {
                    return
                }
                break
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
        val index = elimRand.nextInt(ELIMINATION_ARRAY_SIZE - 1)
        for (i in 1..4) {
            val elementRef = elimination[index].value
            if (elementRef != null && elimination[index].compareAndSet(elementRef, null)) {
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

