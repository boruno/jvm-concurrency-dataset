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
        while (true) {
            val currentTopValue = top.value
            val newTopValue = Node(x, currentTopValue)
            if (top.compareAndSet(currentTopValue, newTopValue)) {
                return
            }
            var indexToPush: Int = 0
            var flag = false
            repeat(100) {
                indexToPush = Random.nextInt(ELIMINATION_ARRAY_SIZE)
                if (eliminationArray[indexToPush].compareAndSet(null, x)) {
                    flag = true
                    return@repeat
                }
            }
            if (flag) {
                repeat(100) {
                    if (eliminationArray[indexToPush].value == null) {
                        return
                    }
                }
            }
        }
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
        while (true) {
            var indexToFetch: Int = 0
            repeat(100) {
                indexToFetch = Random.nextInt(ELIMINATION_ARRAY_SIZE)
                val value = eliminationArray[indexToFetch].value
                if (eliminationArray[indexToFetch].compareAndSet(value, null)) {
//                    println(value)
                    return value
                }
            }

            val currentTopValue = top.value ?: return null
            val newTopValue = currentTopValue.next
            if (top.compareAndSet(currentTopValue, newTopValue)) {
                return currentTopValue.x
            }
        }
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT