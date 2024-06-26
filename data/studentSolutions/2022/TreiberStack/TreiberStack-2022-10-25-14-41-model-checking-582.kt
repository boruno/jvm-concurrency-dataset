package mpp.stack

import kotlinx.atomicfu.atomic
import java.util.concurrent.atomic.AtomicReferenceArray
import kotlin.random.Random

class TreiberStack<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = AtomicReferenceArray<E?>(ELIMINATION_ARRAY_SIZE)

    /**
     * Adds the specified element [x] to the stack.
     */
    fun pushInStack(x: E) {
        while (true) {
            val cur_top = top.value
            val new_top = Node(x, cur_top)
            if (top.compareAndSet(cur_top, new_top))
                return
        }
    }

    fun push(x: E) {
        val index = Random.nextInt(ELIMINATION_ARRAY_SIZE)
        val randomElement = eliminationArray.get(index)
        if (randomElement == null) {
            if (eliminationArray.compareAndSet(index, null, x)) {
                repeat(WAIT_STEPS) {}
                if (eliminationArray.compareAndSet(index, x, null)) {
                    pushInStack(x)
                }
            } else {
                pushInStack(x)
            }
        } else {
            pushInStack(x)
        }
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun popFromStack(): E? {
        while (true) {
            val cur_top = top.value
            if (cur_top == null)
                return null
            val new_top = cur_top.next
            if (top.compareAndSet(cur_top, new_top))
                return cur_top.x
        }
    }

    fun pop(): E? {
        while (true) {
            val index = Random.nextInt(ELIMINATION_ARRAY_SIZE)
            val randomElement = eliminationArray.get(index)
            if (randomElement == null) {
                return popFromStack()
            }
            if (eliminationArray.compareAndSet(index, randomElement, null)) {
                return randomElement
            }
        }
    }

}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT

private const val WAIT_STEPS = 1000