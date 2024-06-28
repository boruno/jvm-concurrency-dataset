package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import kotlin.random.Random


class TreiberStackWithElimination<E> {
    private val random: Random = Random(0)
    private val ELIMINATION_SIZE = 32
    private val WINDOW_SIZE = 4
    private val CNT_NOP = 100
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<Any?>(ELIMINATION_ARRAY_SIZE)

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        if (!tryPushWithElimination(x)) {
            while (true) {
                val curHead = top.value
                val newHead = Node(x, curHead)
                if (top.compareAndSet(curHead, newHead)) return
            }
        }
    }

    private fun tryPushWithElimination(x: E): Boolean {
        val ind: Int = random.nextInt(ELIMINATION_SIZE)
        for (del in 0 until WINDOW_SIZE) {
            val n_ind: Int = (ind + del) % ELIMINATION_SIZE
            if (eliminationArray[n_ind].compareAndSet(null, x)) {
                var cnt: Int = CNT_NOP
                while (cnt-- > 0) {
                    assert(
                        true // Надеюсь компилятор это не убьет.
                    )
                }
                return if (eliminationArray[n_ind].compareAndSet(x, null)) {
                    false
                } else {
                    true
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
        val el = tryPopWithElimination()
        if (el != null) {
            return el
        }
        while (true) {
            val curHead = top.value ?: return null
            if (top.compareAndSet(curHead, curHead.next)) return curHead.x
        }
    }

    fun tryPopWithElimination(): E? {
        val ind: Int = random.nextInt(ELIMINATION_SIZE)
        for (del in 0 until WINDOW_SIZE) {
            val n_ind = (ind + del) % ELIMINATION_SIZE
            val `val` = eliminationArray[n_ind].value
            if (`val` != null && eliminationArray[n_ind].compareAndSet(`val`, null)) {
                return `val` as E?
            }
        }
        return null
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT