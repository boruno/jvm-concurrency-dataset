//package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import kotlin.random.Random

class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<ValueWrapper<E>?>(ELIMINATION_ARRAY_SIZE)

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        fun commonPush(x: E) {
            while (true) {
                val oldHead = top.value
                val newHead = Node(x, oldHead)
                if (top.compareAndSet(oldHead, newHead)) {
                    break
                }
            }
        }

        val xWrapper = ValueWrapper(x)
        val randomIndex = Random.nextInt(0, ELIMINATION_ARRAY_SIZE)
        val randomElement = eliminationArray[randomIndex].value
        if (randomElement == null) {
            if (eliminationArray[randomIndex].compareAndSet(null, xWrapper)) {
                repeat(100) {}
                if (eliminationArray[randomIndex].compareAndSet(xWrapper, null)) {
                    commonPush(x)
                }
            }
        } else {
            commonPush(x)
        }
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
        fun commonPop(): E? {
            while (true) {
                val oldHead = top.value ?: return null
                val newHead = oldHead.next
                if (top.compareAndSet(oldHead, newHead)) {
                    return oldHead.x
                }
            }
        }

        val randomIndex = Random.nextInt(0, ELIMINATION_ARRAY_SIZE)
        while (true) {
            val randomElement = eliminationArray[randomIndex].value ?: return commonPop()
            if (eliminationArray[randomIndex].compareAndSet(randomElement, null)) {
                return randomElement.x
            }
        }
    }
}

private class Node<E>(val x: E, val next: Node<E>?)
private class ValueWrapper<E>(val x: E)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT