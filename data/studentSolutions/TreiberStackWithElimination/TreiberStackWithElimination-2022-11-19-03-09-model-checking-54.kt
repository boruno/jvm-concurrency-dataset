package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import kotlinx.atomicfu.locks.withLock
import java.sql.Timestamp
import java.time.Duration
import java.time.LocalDateTime
import java.time.temporal.TemporalAmount
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.AbstractQueuedSynchronizer.ConditionObject
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.ReentrantLock
import kotlin.random.Random

class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<Any?>(ELIMINATION_ARRAY_SIZE)
    private val MAX_ITER = 2

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        val data = Pair(x, Thread.currentThread().id)
        while (true) {
            val ind = Random.nextInt(ELIMINATION_ARRAY_SIZE)
            if (eliminationArray[ind].compareAndSet(null, data)) {
                var counter = 0
                while (counter < MAX_ITER && eliminationArray[ind].value == data) {
                    counter++
                }

                if (eliminationArray[ind].compareAndSet(data, null)) {
                    while (!oldPushIteration(x)) {
                    }
                }
                return
            }

            if (oldPushIteration(x))
                return
        }
    }

    private fun oldPushIteration(x: E): Boolean {
        val curTop = top.value
        val newTop = Node(x, curTop)
        if (top.compareAndSet(curTop, newTop))
            return true
        return false
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
        while (true) {
            val ind = Random.nextInt(ELIMINATION_ARRAY_SIZE)
            val element = eliminationArray[ind].value
            if (element != null) {
                element as Pair<E, Long>
                if (eliminationArray[ind].compareAndSet(element, null)) {
                    return element.first
                }
            }

            val curTop = top.value ?: return null
            val newTop = curTop.next
            if (top.compareAndSet(curTop, newTop))
                return curTop.x
        }
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT