package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import kotlin.random.Random

class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<Cell<E>?>(ELIMINATION_ARRAY_SIZE)

    private fun pushInStack(x: E) {
        while (true) {
            val cur_top = top.value
            val new_top = Node(x, cur_top)
            if (top.compareAndSet(cur_top, new_top))
                return
        }
    }

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        val newElement = Cell(x)
        val index = Random.nextInt(ELIMINATION_ARRAY_SIZE)
        val randomElement = eliminationArray.get(index).value
        if (randomElement == null) {
            if (eliminationArray.get(index).compareAndSet(null, newElement)) {
                repeat(WAIT_STEPS) {}
                if (eliminationArray.get(index).compareAndSet(newElement, null)) {
                    pushInStack(x)
                }
            } else {
                pushInStack(x)
            }
        } else {
            pushInStack(x)
        }
    }


    private fun popFromStack(): E? {
        while (true) {
            val cur_top = top.value
            if (cur_top == null)
                return null
            val new_top = cur_top.next
            if (top.compareAndSet(cur_top, new_top))
                return cur_top.x
        }
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
        val index = Random.nextInt(ELIMINATION_ARRAY_SIZE)
        while (true) {
            val randomElement = eliminationArray.get(index).value
            if (randomElement == null) {
                return popFromStack()
            }
            if (eliminationArray.get(index).compareAndSet(randomElement, null)) {
                return randomElement.x
            }
        }
    }

}

private class Node<E>(val x: E, val next: Node<E>?)

private class Cell<E>(val x: E)

private const val ELIMINATION_ARRAY_SIZE = 8

private const val WAIT_STEPS = 1000