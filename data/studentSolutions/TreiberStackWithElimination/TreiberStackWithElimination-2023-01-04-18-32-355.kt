//package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.concurrent.ThreadLocalRandom

/**
 * @author : Кулешова Екатерина
 */

class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<E?>(ELIMINATION_ARRAY_SIZE)

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        if (!eliminatePush(x))
            simplePush(x)
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
        return eliminatePop() ?: simplePop()
    }

    private fun eliminatePush(x: E): Boolean {
        val random = ThreadLocalRandom.current()
        var pos = random.nextInt(ELIMINATION_ARRAY_SIZE)

        for (i in 0..COUNT_RETRY) {
            val oldElement = eliminationArray[pos].value
            oldElement ?: let {
                if (eliminationArray[pos].compareAndSet(null, x)) {
                    val startTime = System.nanoTime()
                    while (System.nanoTime() - startTime < WAIT_TIME) {
                        eliminationArray[pos].value ?: return true
                    }
                    eliminationArray[pos].compareAndSet(x, null)
                }
            }
            pos = random.nextInt(ELIMINATION_ARRAY_SIZE)
        }
        return false
    }

    private fun simplePush(x: E) {
        var newTop: Node<E>?
        var curTop: Node<E>?

        do {
            curTop = top.value
            newTop = Node(x, curTop)
        } while (!top.compareAndSet(curTop, newTop))
    }

    private fun eliminatePop(): E? {
        val random = ThreadLocalRandom.current()
        var pos = random.nextInt(ELIMINATION_ARRAY_SIZE)
        var oldElement: E?

        for (i in 0..COUNT_RETRY) {
            oldElement = eliminationArray[pos].value
            oldElement?.let {
                if (eliminationArray[pos].compareAndSet(it, null)) {
                    return oldElement
                }
            }
            pos = random.nextInt(ELIMINATION_ARRAY_SIZE)
        }
        return null
    }

    private fun simplePop(): E? {
        var curTop: Node<E>?

        while (true) {
            curTop = top.value

            return when {
                curTop == null -> null
                top.compareAndSet(curTop, curTop.next) -> curTop.x
                else -> continue
            }
        }
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT
private const val COUNT_RETRY = ELIMINATION_ARRAY_SIZE / 5
private const val WAIT_TIME = 1000L