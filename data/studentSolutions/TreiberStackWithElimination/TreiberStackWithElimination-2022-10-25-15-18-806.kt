//package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.concurrent.TimeUnit

class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<E?>(ELIMINATION_ARRAY_SIZE)

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        if (!tryPush(x)) {
            tryEliminationPush(x)
        }
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
        val result = tryPop()
        if (!result.success) {
            return tryEliminationPop()
        } else {
            return result.value
        }
    }

    private fun tryEliminationPush(x: E) {
        while (true) {
            var pos = 0
            while (pos < ELIMINATION_ARRAY_SIZE) {
                if (eliminationArray[pos].compareAndSet(null, x)) {
                    break
                }
                pos++
            }
            if (pos != ELIMINATION_ARRAY_SIZE) {
                TimeUnit.NANOSECONDS.sleep(1)
                if (!eliminationArray[pos].compareAndSet(x, null)) {
                    return
                }
            }
            if (tryPush(x)) {
                return
            }
        }
    }

    private fun tryEliminationPop(): E? {
        while (true) {
            for (pos in 0 until ELIMINATION_ARRAY_SIZE) {
                val value = eliminationArray[pos].value
                if (eliminationArray[pos].compareAndSet(value, null)) {
                    return value;
                }
            }
            val result = tryPop()
            if (result.success) {
                return result.value
            }
        }
    }

    /**
     * Adds the specified element [x] to the stack.
     */
    private fun tryPush(x: E): Boolean {
//        while (true) {
        val curTop = top.value
        val newTop = Node(x, curTop)
        return top.compareAndSet(curTop, newTop)
//        }
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    private fun tryPop(): TryPopResult<E> {
        val curTop = top.value ?: return TryPopResult(null, true)
        val newTop = curTop.next
        if (top.compareAndSet(curTop, newTop)) {
            return TryPopResult(curTop.x, true)
        } else {
            return TryPopResult(null, false)
        }
    }
}

private class TryPopResult<E>(val value: E?, val success: Boolean)
private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT