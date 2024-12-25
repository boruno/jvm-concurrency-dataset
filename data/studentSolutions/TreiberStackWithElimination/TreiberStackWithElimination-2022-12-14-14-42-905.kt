//package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import kotlin.random.Random

class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<Any?>(ELIMINATION_ARRAY_SIZE)
    private val done = Done()

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        val index = Random.nextInt(ELIMINATION_ARRAY_SIZE)
        if (eliminationArray[index].compareAndSet(null, x)) {
            val item = eliminationArray[index]
            repeat(100) {
                if (item.compareAndSet(done, null)) {
                    return
                }
            }
            item.compareAndSet(x, null)
        } else if (eliminationArray[1 - index].compareAndSet(null, x)) {
            val item = eliminationArray[1 - index]
            repeat(100) {
                if (item.compareAndSet(done, null)) {
                    return
                }
            }
            item.compareAndSet(x, null)
        }
        while (true) {
            val curTop = top.value
            val newTop: Node<E> = Node(x, curTop)
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
        val index = Random.nextInt(ELIMINATION_ARRAY_SIZE)
        if (eliminationArray[index].value != null) {
            val item = eliminationArray[index]
            val ans = item.value
            if (ans !is Done && ans != null && item.compareAndSet(ans, done)) {
                return ans as E
            }
        } else if (eliminationArray[1 - index].value != null) {
            val item = eliminationArray[1 - index]
            val ans = item.value
            if (ans !is Done && ans != null && item.compareAndSet(ans, done)) {
                return ans as E
            }
        }
        while (true) {
            val curTop = top.value
            if (curTop == null) {
                return null
            } else {
                val newTop = curTop.next
                if (top.compareAndSet(curTop, newTop)) {
                    return curTop.x
                }
            }
        }
    }
}

class Done

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT