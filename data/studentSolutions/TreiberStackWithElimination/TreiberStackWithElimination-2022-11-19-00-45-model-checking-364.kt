package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.concurrent.ThreadLocalRandom
import kotlin.random.Random

class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<Any?>(ELIMINATION_ARRAY_SIZE)
    private val random = ThreadLocalRandom.current()

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        var i = random.nextInt(0,  eliminationArray.size - TIMES)
        repeat(TIMES) {
            if (eliminationArray[i].compareAndSet(null, x)) {
                repeat(TIME) {
                    if (eliminationArray[i].compareAndSet(DONE, null)) {
                        return
                    }
                }
                if (eliminationArray[i].compareAndSet(x, null)) {
                    return@repeat
                } else {
                    eliminationArray[i].compareAndSet(DONE, null)
                    return
                }
            }
            i++
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
        var i = random.nextInt(0,  eliminationArray.size - TIMES)
        repeat(TIMES) {
            val x = eliminationArray[i].value
            if (x != null && x != DONE) {
                if (eliminationArray[i].compareAndSet(x, DONE)) {
                    return x as E
                }
            }
            i++
        }

        while (true) {
            val curTop = top.value ?: return null
            val newTop = curTop.next
            if (top.compareAndSet(curTop, newTop)) {
                return curTop.x
            }
        }
    }

    companion object {
        private val DONE = Any()
        private const val TIMES = 2
        private const val TIME = 1_000_000
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT