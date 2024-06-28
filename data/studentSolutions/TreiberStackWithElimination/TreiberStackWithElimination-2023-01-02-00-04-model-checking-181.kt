package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.lang.Thread.sleep
import java.util.concurrent.ThreadLocalRandom
import kotlin.random.Random

class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<Any?>(ELIMINATION_ARRAY_SIZE)

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {

        for (i in 0 until maxIterations) {
            val ind = ThreadLocalRandom.current().nextInt(ELIMINATION_ARRAY_SIZE)
            if (eliminationArray[ind].compareAndSet(null, x)) {
                for (j in 0 until maxIterations) {
                    if (!eliminationArray[ind].compareAndSet(x, null)) {
                        eliminationArray[ind].value = null
                        return
                    }
                }

                if (eliminationArray[ind].compareAndSet(x, null)) {
                    break
                }
                if (eliminationArray[ind].compareAndSet(DONE, null)) {
                    return
                }

            }
        }

        while (true) {
            val curTop = top.value
            val newTop = Node(x, curTop)
            if (top.compareAndSet(curTop, newTop)) {
                return
            }
        }
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    @Suppress("UNCHECKED_CAST")
    fun pop(): E? {

        repeat(maxIterations) {
            val ind = ThreadLocalRandom.current().nextInt(ELIMINATION_ARRAY_SIZE)
            val x = eliminationArray[ind].value
            if (x != null && x != DONE && eliminationArray[ind].compareAndSet(x, DONE)) {
                return x as E
            }
        }

        while (true) {
            val curTop = top.value
            val newTop = curTop?.next
            if (top.compareAndSet(curTop, newTop)) {
                return curTop?.x
            }
        }
    }
}

private class Done()

private val DONE = Done()

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT
private const val maxIterations = 15