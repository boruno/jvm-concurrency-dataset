//package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.Random

@Suppress("UNCHECKED_CAST")
class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<Any?>(ELIMINATION_ARRAY_SIZE)

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        val random = Random()
        while (true) {
            val cur_node = top.value
            val node = Node(x, cur_node)
            if (top.compareAndSet(cur_node, node)) {
                return
            } else {
                val i = random.nextInt(eliminationArray.size - 1)
                if (eliminationArray[i].compareAndSet(null, x)) {
//                    while (eliminationArray[i] == x) {
//
//                    }
                    return
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
//        val random = Random()
        while (true) {
            val cur_node = top.value ?: return null
            val node = cur_node.next
            if (top.compareAndSet(cur_node, node)) {
                return cur_node.x
            } else {
                for (i in 0..eliminationArray.size) {
                    val x = eliminationArray[i].value
                    if (x != null) {
                        eliminationArray[i].compareAndSet(x, null)
                        return x as E?
                    }
                }
            }
        }
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT