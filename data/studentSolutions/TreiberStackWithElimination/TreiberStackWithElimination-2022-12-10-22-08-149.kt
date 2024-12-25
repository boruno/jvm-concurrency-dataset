//package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import kotlin.random.Random

class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<E?>(ELIMINATION_ARRAY_SIZE)
    private val random: Random = Random
    private val RETRIES = 4
    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        if(pushWithElimination(x)) {
            return
        }
        else pushWithoutElimination(x)
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
        val res = popWithElimination()
        return if (res.second) {
            res.first
        } else {
            popWithoutElimination()
        }
    }
    private fun pushWithElimination(x: E): Boolean {
        val ix = random.nextInt(ELIMINATION_ARRAY_SIZE)
        val v = eliminationArray[ix]
        // try to write x if v is empty, else return false on failure 
        for (i in 0..RETRIES) {
            if (v.compareAndSet(null, x)) {
                for (j in 0..RETRIES) {
                    if(v.value == null) {
                        return true
                    }
                }
                return if (v.compareAndSet(x, null)) {
                    false
                } else {
                    true
                }
            }
        }
        return false
    }

    private fun pushWithoutElimination(x: E) {
        while (true) {
            val curTop = top.value
            val newTop = Node(x, curTop)
            if(top.compareAndSet(curTop, newTop)) { break }
        }
    }

    private fun popWithElimination(): Pair<E?, Boolean> {
        val ix = random.nextInt(ELIMINATION_ARRAY_SIZE)
        val v = eliminationArray[ix]
        for (i in 0..RETRIES) {
            val x = v.value
            if(x != null) {
                if(v.compareAndSet(x, null)) {
                    return Pair(x, true)
                }
            }
        }
        return Pair(null, false)
    }
    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    private fun popWithoutElimination(): E? {
        while (true) {
            val curTop = top.value
            val newTop = curTop?.next
            if(top.compareAndSet(curTop, newTop)) { return curTop?.x }
        }
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT