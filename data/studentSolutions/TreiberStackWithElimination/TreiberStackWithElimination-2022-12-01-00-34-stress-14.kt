package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.concurrent.ThreadLocalRandom

class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<Any?>(ELIMINATION_ARRAY_SIZE)


    fun oldPush(x: E) {
        while (true) {
            val curTop = top.value
            val newTop = Node(x, curTop)

            if (top.compareAndSet(curTop, newTop)) {
                return
            }
        }
    }

    fun oldPop(): E? {
        while (true) {
            val curTop = top.value ?: return null
            val newTop = curTop.next
            if (top.compareAndSet(curTop, newTop)) {
                return curTop.x
            }
        }
    }

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {

        val elimIndex = ThreadLocalRandom.current().nextInt(ELIMINATION_ARRAY_SIZE)
        if (eliminationArray[elimIndex].compareAndSet(null, x)) {
            var i = 0
            while (i != 100) {
                i += 1
                if (!eliminationArray[elimIndex].compareAndSet(x, x)) {
                    return
                }
            }
            oldPush(x)
        } else {
            oldPush(x)
        }

    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
        val elimIndex = ThreadLocalRandom.current().nextInt(ELIMINATION_ARRAY_SIZE)
        return if (!eliminationArray[elimIndex].compareAndSet(null, null)) {
            val value = eliminationArray[elimIndex].value
            if (eliminationArray[elimIndex].compareAndSet(value, null)) {
                value as E
            } else {
                oldPop()
            }
        } else {
            oldPop()
        }
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT