//package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.concurrent.ThreadLocalRandom

class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<Any?>(ELIMINATION_ARRAY_SIZE)
    private val eliminationArrayStates = atomicArrayOfNulls<String>(ELIMINATION_ARRAY_SIZE)
    private val indexGenerator = ThreadLocalRandom.current()

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        if (!tryPush(x))
            treiberPush(x)
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
        return tryPop() ?: treiberPop()

    }

    private fun treiberPush(x: E) {
        while (true) {
            val node = Node(x, top.value)
            if (top.compareAndSet(node.next, node))
                break
        }
    }

    private fun treiberPop(): E? {
        while (true) {
            val node = top.value ?: return null
            if (top.compareAndSet(node, node.next))
                return node.x
        }
    }

/*    private fun tryPush(x: E): Boolean {
        val index = indexGenerator.nextInt(ELIMINATION_ARRAY_SIZE)

        if (eliminationArray[index].compareAndSet(null, x)) {
            eliminationArrayStates[index].value = WAITING
            for (i in (0..ELIMINATION_AWAIT)) {
                if (eliminationArrayStates[index].compareAndSet(DONE, null)) {
                    eliminationArray[index].value = null
                    return true
                }
            }

            eliminationArray[index].value = null
            val isLateDone = eliminationArrayStates[index].compareAndSet(WAITING, null)
            return isLateDone
        }

        return false
    }

    private fun tryPop(): E? {
        val index = indexGenerator.nextInt(ELIMINATION_ARRAY_SIZE)

        for (i in (0..ELIMINATION_AWAIT))
            if (eliminationArrayStates[index].compareAndSet(WAITING, GETTING)) {
                val x = eliminationArray[index].value
                eliminationArrayStates[index].value = DONE
                return x
            }
        return null
    }
 */

    private fun tryPush(x: E): Boolean {
        val index = indexGenerator.nextInt(ELIMINATION_ARRAY_SIZE)

        if (eliminationArray[index].compareAndSet(null, x)) {
            for (i in (0..ELIMINATION_AWAIT)) {
                if (eliminationArray[index].compareAndSet(DONE, null)) {
                    return true
                }
            }
            return !eliminationArray[index].compareAndSet(DONE, null)
        }

        return false
    }

    private fun tryPop(): E? {
        val index = indexGenerator.nextInt(ELIMINATION_ARRAY_SIZE)

        for (i in (0..ELIMINATION_AWAIT)) {
            val x = eliminationArray[index].value
            if (eliminationArray[index].compareAndSet(x, DONE)) {
                return x as? E
            }
        }
        return null
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT

private const val ELIMINATION_AWAIT = 100

private const val DONE = "DONE"
