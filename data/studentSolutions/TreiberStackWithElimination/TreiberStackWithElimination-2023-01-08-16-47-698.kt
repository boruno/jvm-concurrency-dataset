package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.concurrent.ThreadLocalRandom
class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<E?>(ELIMINATION_ARRAY_SIZE)
    private val eliminationArrayStates = atomicArrayOfNulls<String>(ELIMINATION_ARRAY_SIZE)
    private val indexGenerator = ThreadLocalRandom.current()

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        if (!tryPush(x))
            tryPush(x)
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
        return tryPop() ?: treiberPop()
    }

    private fun treiberPush(x: E){
        while (true) {
            val node = Node(x, top.value)
            if (top.compareAndSet(node.next, node))
                break
        }
    }

    private fun treiberPop(): E?{
        while (true) {
            val node = top.value ?: return null
            if (top.compareAndSet(node, node.next))
                return node.x
        }
    }

    private fun tryPush(x: E): Boolean {
        return false
    }

    private fun tryPop(): E? {
        return null
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT

private const val ELIMINATION_AWAIT = 30