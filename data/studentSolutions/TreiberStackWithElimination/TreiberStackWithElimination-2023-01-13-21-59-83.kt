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
        var randInd = Random.nextInt(ELIMINATION_ARRAY_SIZE)
        // try to push the element into eliminationArray
        for (i in 0 until ELIMINATION_ARRAY_SIZE) {
            randInd = (randInd + i) % ELIMINATION_ARRAY_SIZE
            val positionReference = eliminationArray[randInd]
            if (positionReference.compareAndSet(null, x)) {
                // wait and listen to pop
                for (j in 0 until WAIT_ITERATIONS) {
                    // if pop() occurred
                    if (positionReference.compareAndSet(null, null)) {
                        return
                    }
                }
                positionReference.compareAndSet(x, null)
            }
        }

        while (true) {
            val currentTop = top.value
            if (top.compareAndSet(currentTop, Node(x, currentTop))) {
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
        var randInd = Random.nextInt(ELIMINATION_ARRAY_SIZE)
        for (i in 0 until ELIMINATION_ARRAY_SIZE) {
            randInd = (randInd + i) % ELIMINATION_ARRAY_SIZE
            val el = eliminationArray[randInd].getAndSet(null)
            if (el != null) {
                return el
            }
        }

        while (true) {
            val currentTop = top.value ?: return null
            if (top.compareAndSet(currentTop, currentTop.next)) {
                return currentTop.x
            }
        }
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT
private const val WAIT_ITERATIONS = 100