//package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import kotlin.random.Random

class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<Box<E>?>(ELIMINATION_ARRAY_SIZE)

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        val box = Box(x)
        while (true) {
            val topCopy = top.value
            val newTop = Node(x, topCopy)
            if (top.compareAndSet(topCopy, newTop)) {
                return
            }
            if (tryPushElimination(box)) {
                return
            }
        }
    }

    private fun tryPushElimination(box: Box<E>): Boolean {
        val index = Random.nextInt() % eliminationArray.size
        val cell = eliminationArray[index]
        if (cell.compareAndSet(null, box)) {
            for (k in 1..ITERATIONS_TO_WAIT) {
                if (cell.value != box) {
                    return true
                }
            }
            if (!cell.compareAndSet(box, null)) {
                return true
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
        while (true) {
            val topCopy = top.value ?: return null
            if (top.compareAndSet(topCopy, topCopy.next)) {
                return topCopy.x
            }
            tryPopElimination()?.let { return it }
        }
    }

    private fun tryPopElimination(): E? {
        for (i in 0 until eliminationArray.size) {
            val cell = eliminationArray[i]
            val node = cell.value
            if (node != null && cell.compareAndSet(node, null)) {
                return node.x
            }
        }
        return null
    }

    companion object {
        private const val ITERATIONS_TO_WAIT = 30
    }
}

private class Box<E>(val x: E)

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT