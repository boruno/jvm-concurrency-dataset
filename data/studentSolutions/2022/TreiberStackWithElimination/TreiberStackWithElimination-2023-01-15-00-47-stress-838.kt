package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import kotlin.random.Random

class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<Any?>(ELIMINATION_ARRAY_SIZE)

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        var index: Int = -1
        repeat(ELIMINATION_ARRAY_SIZE) {
            index = Random.nextInt(ELIMINATION_ARRAY_SIZE)
            if (eliminationArray[index].compareAndSet(null, x)) {
                return@repeat
            } else {
                index = -1
            }
        }

        repeat(MAX_ROTATIONS) {
            if (index >= 0 && eliminationArray[index].compareAndSet(null, null)) {
                return
            }
        }

        if (index >= 0 && !eliminationArray[index].compareAndSet(x, null)) {
            return
        }

        do {
            val before = top.value
            val after = Node(x, before)
        } while (!top.compareAndSet(before, after))
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    @Suppress("UNCHECKED_CAST")
    fun pop(): E? {
        repeat(MAX_ROTATIONS) {
            eliminationArray[Random.nextInt(ELIMINATION_ARRAY_SIZE)].getAndSet(null)?.let { return it as E }
        }

        var before: Node<E>?
        do {
            before = top.value ?: return null

            val after = before.next
        } while (!top.compareAndSet(before, after))

        return before?.x
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT
private const val MAX_ROTATIONS = 10
