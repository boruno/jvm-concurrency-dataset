//package mpp.stackWithElimination

import com.sun.org.apache.xpath.internal.operations.Bool
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
        while (true) {
            if(pushWithElimination(x)) {
                return
            }
            if (pushWithoutElimination(x)) {
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
        while (true) {
            val res = popWithElimination()
            if (res.second) {
                return res.first
            }
            val res2 = popWithoutElimination()
            if(res2.second) {
                return res2.first
            }
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

    private fun pushWithoutElimination(x: E): Boolean {
        val curTop = top.value
        val newTop = Node(x, curTop)
        return top.compareAndSet(curTop, newTop)
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
    private fun popWithoutElimination(): Pair<E?, Boolean> {
        val curTop = top.value
        val newTop = curTop?.next
        return if(top.compareAndSet(curTop, newTop)) {
            Pair(curTop?.x, true)
        } else {
            Pair(null, false)
        }

    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT