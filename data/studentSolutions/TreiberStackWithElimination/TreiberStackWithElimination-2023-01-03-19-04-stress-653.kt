package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import java.util.*
import java.util.concurrent.atomic.AtomicReferenceArray

class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = AtomicReferenceArray<Node<E>?>(ELIMINATION_ARRAY_SIZE)
    private val random = Random()

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        val cellNumber = random.nextInt(ELIMINATION_ARRAY_SIZE)
        val newNode = Node(x, null)
        loop@ for (i in 0..100) {
            if (eliminationArray.compareAndSet(cellNumber, null, newNode)) {
                for (j in 0..1000) {
                    if (eliminationArray.compareAndSet(cellNumber, null, null)) {
                        return
                    }
                }
                for (k in 0..1000) {
                    if (eliminationArray.compareAndSet(cellNumber, newNode, null)) {
                        break@loop
                    }
                }
            }
        }
        pushToStack(x)
    }

    private fun pushToStack(x: E) {
        while (true) {
            val currentTop = top.value
            val newTop = Node(x = x, next = currentTop)
            if (top.compareAndSet(currentTop, newTop)) return
        }
    }

    private fun popFromStack(): E? {
        while (true) {
            val currentTop = top.value ?: return null
            val nextTop = currentTop.next
            if (top.compareAndSet(currentTop, nextTop)) return currentTop.x
        }
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
        val cellNumber = random.nextInt(2)
        val cell = eliminationArray.get(cellNumber)
        if (cell == null) {
            return popFromStack()
        } else {
            for (i in 0..500) {
                if (eliminationArray.compareAndSet(cellNumber, cell, null)) {
                    return cell.x
                }
            }
            return popFromStack()
        }
    }
}

private class Node<E>(val x: E, val next: Node<E>?) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Node<*>

        if (x != other.x) return false
        if (next != other.next) return false

        return true
    }

    override fun hashCode(): Int {
        var result = x?.hashCode() ?: 0
        result = 31 * result + (next?.hashCode() ?: 0)
        return result
    }
}

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT