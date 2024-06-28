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

        while (true) {
            val ind = ThreadLocalRandom.current().nextInt() % ELIMINATION_ARRAY_SIZE
            if (eliminationArray[ind].compareAndSet(null, x)) {
                sleep(1000)
                if (!eliminationArray[ind].compareAndSet(x, null)) {
                    eliminationArray[ind].value = null
                    return
                }
            }



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

        while (true) {
            val ind = ThreadLocalRandom.current().nextInt() % ELIMINATION_ARRAY_SIZE
            val x = eliminationArray[ind].value
            if (x != null && x != DONE && eliminationArray[ind].compareAndSet(x, DONE)) {
                return x as E
            }

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