//package mpp.stackWithElimination

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
        val randomIndex = Random.nextInt(ELIMINATION_ARRAY_SIZE);
        while (true) {
            if (tryPush(x)) {
                return
            }
            if (eliminationArray[randomIndex].compareAndSet(null, x)) {
                repeat(100) {}
                if (eliminationArray[randomIndex].compareAndSet(x, null)) {
                    return
                }
            }
        }

//        val randomIndex = Random.nextInt(ELIMINATION_ARRAY_SIZE)
//        val node = eliminationArray[randomIndex].value
//        if (node != null) insert(x)
//        if (eliminationArray[randomIndex].compareAndSet(null, x)) {
//            repeat(100) {}
//            if (eliminationArray[randomIndex].compareAndSet(x, null)) {
//                insert(x)
//            }
//        } else {
//            insert(x)
//        }
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
        val randomIndex = Random.nextInt(ELIMINATION_ARRAY_SIZE)
        while (true) {
            val node = getNode()
            if (node != null) {
                return node.x
            } else {
                val x = eliminationArray[randomIndex].value
                if (eliminationArray[randomIndex].compareAndSet(x, null)) {
                    return x as E
                }
            }
        }
    }

    private fun tryPush(x: E): Boolean {
        val currentTop = top.value
        val newTop = Node(x, currentTop)
        return top.compareAndSet(currentTop, newTop)
    }

    private fun getNode(): Node<E>? {
        val currentTop = top.value ?: return null
        val newTop = currentTop.next
        return if (top.compareAndSet(currentTop, newTop)) {
            currentTop
        } else {
            null
        }
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT