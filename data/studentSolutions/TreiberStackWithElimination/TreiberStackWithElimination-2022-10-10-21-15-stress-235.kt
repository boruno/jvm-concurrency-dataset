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

        var index = Random.nextInt(ELIMINATION_ARRAY_SIZE)
        if (eliminationArray[index].compareAndSet(null, x)) {
            for (i in 1..10) {} // wait
            if (eliminationArray[index].compareAndSet(x, null)) {
                // need push
            } else if (eliminationArray[index].compareAndSet(Done(), null)) {
                return
            }
        }

        do {
            var old_top = top.value
        } while (!top.compareAndSet(old_top, Node<E>(x, old_top)))
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
        var index = Random.nextInt(ELIMINATION_ARRAY_SIZE)
        var value = eliminationArray[index].value
        if (value != null && (value !is Done) && eliminationArray[index].compareAndSet(value, Done())) {
            return value as E
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