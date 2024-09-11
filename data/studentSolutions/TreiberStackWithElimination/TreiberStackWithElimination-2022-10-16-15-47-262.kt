package mpp.stackWithElimination

import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import javax.swing.text.StyledEditorKit.BoldAction

import kotlin.random.Random;

class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<E>(ELIMINATION_ARRAY_SIZE)

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        if (tryPushToEliminationArray(x)) {
            return
        }

        do {
            val oldHead = top.value
            val newHead = Node(x, oldHead)
        } while (!top.compareAndSet(oldHead, newHead))
    }

    private fun tryPushToEliminationArray(x: E): Boolean {
        val i = Random.nextInt(ELIMINATION_ARRAY_SIZE)
        val cell = eliminationArray[i] // Случайная ячейка
        if (!cell.compareAndSet(null, x)) {
            return false // 
        }
        for (time in 0..3) {
            if (cell.compareAndSet(null, null)) {
                return true;
            }
        }
        return !cell.compareAndSet(x, null)
    }

    private fun tryPopFromEliminationArray(): E? {
        val i = Random.nextInt(ELIMINATION_ARRAY_SIZE)
        val cell = eliminationArray[i]
        for (time in 0..3) {
            val x = cell.getAndSet(null)
            if (x != null) {
                return x
            }
        }
        return null
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
        var result = tryPopFromEliminationArray()
        if (result != null) {
            return result
        }
        do {
            val oldHead = top.value ?: return null
            result = oldHead.x
            val newHead = oldHead.next
        } while (!top.compareAndSet(oldHead, newHead))
        return result
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT