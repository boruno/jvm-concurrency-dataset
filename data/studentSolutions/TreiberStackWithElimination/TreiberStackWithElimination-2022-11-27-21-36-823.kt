//package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import kotlin.random.Random

class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<E?>(ELIMINATION_ARRAY_SIZE)

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
    private fun pushOptimization(x: E): Boolean {
        val pos = Random.nextInt(0, ELIMINATION_ARRAY_SIZE)
        if (eliminationArray[pos].compareAndSet(null, x)) {
            repeat(100) {
                if (eliminationArray[pos].value == null) return true
            }
            return !eliminationArray[pos].compareAndSet(x, null)
        }
        return false
    }

    private fun popOptimization(): E? {
        val pos = Random.nextInt(0, ELIMINATION_ARRAY_SIZE)
        val curValue = eliminationArray[pos].value
        return if (eliminationArray[pos].compareAndSet(curValue, null)) curValue
        else null
    }

}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT