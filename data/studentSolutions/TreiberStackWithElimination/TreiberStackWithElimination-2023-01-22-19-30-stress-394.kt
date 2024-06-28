package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import kotlin.random.Random

class TreiberStackWithElimination<E : Any> {
    private val head = atomic<Node<E>?>(null)
    private val array = atomicArrayOfNulls<Any?>(ELIMINATION_ARRAY_SIZE)
    private val SPIN = 30
    private val NEAR = 2
    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        var rnd = Random.nextInt(ELIMINATION_ARRAY_SIZE)
        for (i in 0 until NEAR) {
            if (rnd == ELIMINATION_ARRAY_SIZE) rnd = 0
            if (help(rnd, x)) return
            rnd++
        }
        while (true) {
            val headLast = head.value
            val nod = Node(x, headLast)
            if (head.compareAndSet(headLast, nod)) return
        }
    }

    private fun help(inx: Int, x: E): Boolean {
        val ar = array[inx]
        if (ar.compareAndSet(null, x)) {
            for (i in 0 until SPIN) {
                if (ar.compareAndSet(Integer.MAX_VALUE, null)) return true
            }
            when {
                !ar.compareAndSet(x, null) -> {
                    ar.compareAndSet(Integer.MAX_VALUE, null)
                    return true
                }
            }
        }
        return false
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
        var rnd = Random.nextInt(ELIMINATION_ARRAY_SIZE)
        for (i in 0 until NEAR) {
            if (rnd == ELIMINATION_ARRAY_SIZE) rnd = 0
            val last = array[rnd]
            val lv = last.value
            if (lv != null && last.compareAndSet(lv, Integer.MAX_VALUE) && lv != Integer.MAX_VALUE) return lv as E?
            rnd++
        }
        while (true) {
            val headLast = head.value
            if (headLast == null) return Integer.MIN_VALUE as E?
            if (head.compareAndSet(headLast, headLast.next)) return headLast.x
        }
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT
