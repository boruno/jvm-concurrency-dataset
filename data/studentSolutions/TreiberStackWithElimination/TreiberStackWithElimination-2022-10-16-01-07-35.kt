//package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*

class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<E?>(ELIMINATION_ARRAY_SIZE)
    private val randomizer = Random()

    /**
     * Adds the specified element [x] to the stack.
     */
    private fun tryToEliminatePush(x: E): Boolean {
        var index: Int

        do {
            index = randomizer.nextInt(ELIMINATION_ARRAY_SIZE) % ELIMINATION_ARRAY_SIZE
        } while(eliminationArray[index].value != null)

        eliminationArray[index].value = x

        for (i in 0..50 step 1) {
            if(eliminationArray[index].value == null) {
                return true
            }
        }
        eliminationArray[index].value = null
        return false
    }

    private fun tryToEliminatePop(): E? {
        for (i in 0..ELIMINATION_ARRAY_SIZE) {
            if(eliminationArray[i].value != null) {
                val pop_result = eliminationArray[i].value
                eliminationArray[i].value = null
                return pop_result
            }
        }
        return null
    }

    fun push(x: E) {
        if (tryToEliminatePush(x)) { return }

        while (true) {
            val currentTop = top.value
            val newTop = Node<E>(x, currentTop)
            if (top.compareAndSet(currentTop, newTop)) {
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
        val x = tryToEliminatePop()
        if (x != null) {
            return x
        }

        while(true) {
            val currentTop = top.value ?: return null
            val newTop = currentTop.next
            if(top.compareAndSet(currentTop, newTop)) {
                return currentTop.x
            }
        }
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT