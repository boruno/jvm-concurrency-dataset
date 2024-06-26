package mpp.stackWithElimination

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
        val index = Random.nextInt(ELIMINATION_ARRAY_SIZE)
        var i = 0

        while (i < PUSH_TRIES && i + index < ELIMINATION_ARRAY_SIZE) {
            var found = false

            for (j in 0 until PUSH_WAIT) {
                if (eliminationArray[i].compareAndSet(null, x)) {
                    if (eliminationArray[i].compareAndSet(x, null)) {
                        found = true
                        break
                    } else {
                        return
                    }
                }
            }

            if (found) {
                break
            }

            i++
        }

        fallbackPush(x)
    }

    private fun fallbackPush(x: E) {
        while (true) {
            val curHead = top.value
            val newHead = Node(x, curHead)
            if (top.compareAndSet(curHead, newHead)) {
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
        val index = Random.nextInt(ELIMINATION_ARRAY_SIZE)

        var i = 0
        while (i < POP_TRIES && index + i < ELIMINATION_ARRAY_SIZE) {
            val value = eliminationArray[i].value

            if (value == null) {
                i++
                continue
            }
            if (eliminationArray[i].compareAndSet(value, null)) {
                return value
            }
            i++
        }

        return fallbackPop()
    }

    private fun fallbackPop(): E? {
        while (true) {
            val curHead = top.value ?: return null
            if (top.compareAndSet(curHead, curHead.next)) {
                return curHead.x
            }
        }
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT
private const val PUSH_WAIT = 256
private const val PUSH_TRIES = 8
private const val POP_TRIES = 8