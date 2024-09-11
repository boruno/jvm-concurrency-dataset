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
        var value = x
        var index = Random.nextInt(ELIMINATION_ARRAY_SIZE - 1)
        if (eliminationArray.get(index).compareAndSet(null, value)) {
            var _value = eliminationArray.get(index).getAndSet(null)
            if (_value == null) return
            value = _value
        }

        do {
            var old_top = top.value
        } while (!top.compareAndSet(old_top, Node<E>(value, old_top)))
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
        var index = Random.nextInt(ELIMINATION_ARRAY_SIZE - 1)
        var value = eliminationArray.get(index).getAndSet(null)
        if (value != null) {
            return value
        }

        while (true) {
            var old_top = top.value
            if (old_top == null) {
                return null
            }
            if (top.compareAndSet(old_top, old_top.next)) {
                return old_top.x
            }
        }
    }
}

private class Node<E>(val x: E, val next: Node<E>?)
private class Done()

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT