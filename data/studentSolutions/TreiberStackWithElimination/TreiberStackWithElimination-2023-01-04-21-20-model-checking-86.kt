package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls


class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<Any?>(ELIMINATION_ARRAY_SIZE)

    private fun pushInStack(x: E) {
        while (true) {
            val cur_top = top.value
            val new_top = Node(x, cur_top)
            if (top.compareAndSet(cur_top, new_top))
                return
        }
    }

    fun push(x: E) {
        val newElement = x
        val index = (0..ELIMINATION_ARRAY_SIZE).random()
        val randomElement = eliminationArray.get(index).value
        if (randomElement == null) {
            if (eliminationArray.get(index).compareAndSet(null, newElement)) {
                repeat(1000) {}
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

    fun pop(): E? {
        val index = (0..ELIMINATION_ARRAY_SIZE).random()
        while (true) {
            val randomElement = eliminationArray.get(index).value
            if (randomElement == null) {
                return popFromStack()
            }
        }
    }



}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT