//package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls

import kotlin.random.Random

class TreiberStackWithElimination<E> {
    class DoneState

    private val done = DoneState()
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<Any?>(ELIMINATION_ARRAY_SIZE)

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        if (!fastPush(x)) {
            basicPush(x)
        }
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
        return fastPop() ?: return basicPop()
    }

    private fun fastPush(x: E): Boolean {
        val i = Random.nextInt(ELIMINATION_ARRAY_SIZE)
        for (attempt in 0 until ATTEMPTS_COUNT) {
            val cell = eliminationArray[(i + attempt) % ELIMINATION_ARRAY_SIZE]
            if (!cell.compareAndSet(null, x)) {
                continue
            }
            for (time in 0 until WAITING_TIME) {
                if (cell.compareAndSet(done, null)) {
                    return true
                }
            }
            return !cell.compareAndSet(x, null)
        }
        return false
    }

    private fun basicPush(x: E) {
        do {
            val oldHead = top.value
            val newHead = Node(x, oldHead)
        } while (!top.compareAndSet(oldHead, newHead))
    }

    @Suppress("UNCHECKED_CAST")
    private fun fastPop(): E? {
        val i = Random.nextInt(ELIMINATION_ARRAY_SIZE)
        for (attempt in 0 until ATTEMPTS_COUNT) {
            val cell = eliminationArray[i]
            for (time in 0 until WAITING_TIME) {
                val x = cell.getAndSet(done)
                if (x == null) {
                    cell.value = null
                } else if (x !is DoneState) {
                    return x as E
                }
            }
        }
        return null
    }

    private fun basicPop(): E? {
        var result: E?
        do {
            val oldHead = top.value ?: return null
            result = oldHead.x
            val newHead = oldHead.next
        } while (!top.compareAndSet(oldHead, newHead))
        return result
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT
private const val ATTEMPTS_COUNT = 2
private const val WAITING_TIME = 3
