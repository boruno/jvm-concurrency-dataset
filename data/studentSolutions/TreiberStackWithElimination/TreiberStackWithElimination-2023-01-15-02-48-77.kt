//package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import kotlinx.atomicfu.getAndUpdate
import kotlin.random.Random

private data class DoneAction(val useless: Int = 0)

class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<Any?>(ELIMINATION_ARRAY_SIZE)

    private val rnd = { Random.nextInt(ELIMINATION_ARRAY_SIZE) }

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        val index = rnd()
        if (eliminationArray[index].compareAndSet(null, x)) {
            repeat(MAX_ROTATIONS) {
                if (eliminationArray[index].compareAndSet(DoneAction(), null)) {
                    return
                }
            }

            if (eliminationArray[index].getAndSet(null) != x) {
                return
            }
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
            val index = rnd()
            val value = eliminationArray[index].value
            if (value !is DoneAction) {
                eliminationArray[index].compareAndSet(value, DoneAction())?.let { return it as E }
            }
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
private const val MAX_ROTATIONS = 3
