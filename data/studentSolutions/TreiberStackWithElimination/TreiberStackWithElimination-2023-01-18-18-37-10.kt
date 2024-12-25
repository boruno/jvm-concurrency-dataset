//package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*


class TreiberStackWithElimination<E> {
    private val head = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<E>(ELIMINATION_SIZE)

    fun push(x: E) {
        if (!pushWithElimination(x)) {
            while (true) {
                val curHead = head.value
                val newHead = Node(x, curHead)
                if (head.compareAndSet(curHead, newHead)) {
                    return
                }
            }
        }
    }

    private fun pushWithElimination(x: E): Boolean {
        val init = random.nextInt(ELIMINATION_SIZE - WINDOW_SIZE)
        for (i in init until init + WINDOW_SIZE) {
            if (eliminationArray[i].compareAndSet(null, x)) {
                for (j in 0..29) {
                    val value = eliminationArray[i].value
                    if (value == null || value != x) {
                        return true
                    }
                }
                return !eliminationArray[i].compareAndSet(x, null)
            }
        }
        return false
    }

    fun pop(): E? {
        val init = random.nextInt(ELIMINATION_SIZE - WINDOW_SIZE)
        for (i in init until init + WINDOW_SIZE) {
            val value = eliminationArray[i].value!!
            if (eliminationArray[i].compareAndSet(value, null)) {
                return value
            }
        }
        while (true) {
            val currHead = head.value ?: return null
            if (head.compareAndSet(currHead, currHead.next)) {
                return currHead.x
            }
        }
    }

    private class Node<E>(val x: E, val next: Node<E>?)

    companion object {
        private val random = Random(0)
        private const val ELIMINATION_SIZE = 32
        private const val WINDOW_SIZE = 4
    }
}
