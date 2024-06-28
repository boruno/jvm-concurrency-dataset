package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.concurrent.ThreadLocalRandom

class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<Pair<E>?>(ELIMINATION_ARRAY_SIZE)

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        val pair = Pair(x, Thread.currentThread().id)
        val index = (0..2).shuffled().first()
        if (eliminationArray[index].compareAndSet(null, pair)) {
            if (!eliminationArray[index].compareAndSet(pair, null)) {
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
        val index = (0..2).shuffled().first()
        val pair = eliminationArray[index].getAndSet(null)
        var x = pair?.x
        if (x == null) {
            while (true) {
                val curTop = top.value ?: return null
                val newTop = curTop.next
                if (top.compareAndSet(curTop, newTop)) {
                    x = curTop.x
                    break
                }
            }
        }
        return x
    }
}

private class Pair<E>(val x: E, val thread: Long)

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT