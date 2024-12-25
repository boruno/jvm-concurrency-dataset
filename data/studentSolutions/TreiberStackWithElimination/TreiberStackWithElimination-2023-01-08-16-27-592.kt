//package mpp.stackWithElimination

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
        if (tryPush(x))
            return

        while (true) {
            val node = Node(x, top.value)
            if (top.compareAndSet(node.next, node))
                break
        }
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
        val x = tryPop()
        if (x != null)
            return x

        while (true) {
            val node = top.value ?: return null
            if (top.compareAndSet(node, node.next))
                return node.x
        }
    }

    private fun tryPush(x: E): Boolean {
        val index = indexGenerator.nextInt(ELIMINATION_ARRAY_SIZE)
        if (eliminationArray[index].compareAndSet(null, x)) {
            eliminationArrayStates[index].value = IN_PROGRESS
            for (i in (0..PUSH_ELIMINATION_AWAIT))
                if (eliminationArrayStates[index].value == DONE) {
                    eliminationArray[index].value = null
                    eliminationArrayStates[index].value = FREE
                    return true
                }
        }

        return false
    }

    private fun tryPop(): E? {
        val index = indexGenerator.nextInt(ELIMINATION_ARRAY_SIZE)
        for (i in (0..POP_ELIMINATION_AWAIT)) {
            val x = eliminationArray[index].value
            if (eliminationArrayStates[index].compareAndSet(IN_PROGRESS, DONE))
                return x
        }

        return null
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT

private const val PUSH_ELIMINATION_AWAIT = 30

private const val POP_ELIMINATION_AWAIT = 10

private const val DONE = "DONE"

private const val IN_PROGRESS = "IN_PROGRESS"

private const val FREE = "FREE"