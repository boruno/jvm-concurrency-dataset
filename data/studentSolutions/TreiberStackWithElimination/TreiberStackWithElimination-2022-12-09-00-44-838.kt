//package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import kotlin.math.abs
import kotlin.random.Random

private class PushInfo<E>(val value: E?, val id: Long)

class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<PushInfo<E>>(ELIMINATION_ARRAY_SIZE)

    private fun getRandPos(): Int {
        return abs(Random.nextInt()) % ELIMINATION_ARRAY_SIZE
    }

    private fun tryWriteToEliminationArray(x: E?): Pair<Boolean, Int> {
        val pushInfo = PushInfo(x, Thread.currentThread().id)
        for (i in 0 until ELIMINATION_ITERS) {
            val pos = getRandPos()
            if (eliminationArray[pos].compareAndSet(null, pushInfo)) {
                return true to pos
            }
        }
        return false to -1
    }

    private fun tryEliminate(): E? {
        for (i in 0 until ELIMINATION_ITERS) {
            val pos = getRandPos()
            val ret = eliminationArray[pos].getAndSet(null)
            if (ret != null) {
                return ret.value
            }
        }
        return null
    }

    private fun waitElimination(pos: Int, x: E?): Boolean {
        val pushInfo = PushInfo(x, Thread.currentThread().id)
        for (i in 0 until WAIT_MEET_ITERS) {
            if (eliminationArray[pos].compareAndSet(null, null)) {
                return true
            }
        }
        return !eliminationArray[pos].compareAndSet(pushInfo, null)
    }

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        val (put, pos) = tryWriteToEliminationArray(x)
        if (put) {
            if (waitElimination(pos, x)) {
                return
            }
        }

        var oldTop: Node<E>?
        val newTop = Node(x, null)
        do {
            oldTop = top.value
            newTop.next = oldTop
        } while (!top.compareAndSet(oldTop, newTop))
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
        val res = tryEliminate()
        if (res != null) {
            return res
        }

        var oldTop: Node<E>?
        var newTop: Node<E>?
        do {
            oldTop = top.value
            newTop = oldTop?.next
        } while (!top.compareAndSet(oldTop, newTop))
        return oldTop?.x
    }
}

private class Node<E>(val x: E, var next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT
private const val ELIMINATION_ITERS = 3
private const val WAIT_MEET_ITERS = 10
