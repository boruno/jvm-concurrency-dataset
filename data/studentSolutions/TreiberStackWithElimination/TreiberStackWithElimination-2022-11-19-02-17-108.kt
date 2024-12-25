//package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.sql.Timestamp
import java.time.Duration
import java.time.LocalDateTime
import java.time.temporal.TemporalAmount
import kotlin.random.Random

class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<Any?>(ELIMINATION_ARRAY_SIZE)

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        println("push")
        val lock = Object()
        val lock2 = Object()
        val data = Pair(x, lock)
        while (true) {
            var ind = Random.nextInt(ELIMINATION_ARRAY_SIZE)
            ind = 1
            if (eliminationArray[ind].compareAndSet(null, data)) {
                synchronized(lock2) {
                    val time = LocalDateTime.now()
                    println("Time: ${LocalDateTime.now()}")
                    lock.wait(10000)
                    println("Push in: ${Duration.between(time, LocalDateTime.now())}")
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
        println("Pop")
        while (true) {
            var ind = Random.nextInt(ELIMINATION_ARRAY_SIZE)
            ind = 1
            val element = eliminationArray[ind].value
            if (element != null) {
                element as Pair<E, Object>
                if (eliminationArray[ind].compareAndSet(element, null)) {
                    synchronized(element.second) {
                        element.second.notify()
                    }
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