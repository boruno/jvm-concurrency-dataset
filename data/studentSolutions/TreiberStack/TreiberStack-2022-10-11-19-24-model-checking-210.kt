package mpp.stack

import kotlinx.atomicfu.AtomicArray
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls

class TreiberStack<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray : AtomicArray<E?> = atomicArrayOfNulls(ELIMINATION_ARRAY_SIZE)

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        while (true) {
            if (pushOptimization(x)) return
            val curTop = top.value
            val newTop = Node(x, curTop)
            if (top.compareAndSet(curTop, newTop)) return
        }
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
        while (true) {
            val optimizedValue = popOptimization()
            if (optimizedValue != null) return optimizedValue
            val curTop = top.value ?: return null
            val newTop = curTop.next
            if (top.compareAndSet(curTop, newTop)) return curTop.x
        }
    }

    //Optimizations:
    private fun pushOptimization(x: E) : Boolean {
        val pos = (0 until ELIMINATION_ARRAY_SIZE).shuffled().last()
        if (eliminationArray[pos].compareAndSet(null, x)) {
            repeat(1000) {
                if (eliminationArray[pos].value == null) return false
            }
            return true
        }
        return false
    }

    fun popOptimization() : E? {
        val pos = (0 until ELIMINATION_ARRAY_SIZE).random()
        val curValue = eliminationArray[pos].value
        return if (eliminationArray[pos].compareAndSet(curValue, null)) curValue
        else null
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT