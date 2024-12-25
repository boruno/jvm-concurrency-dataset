//package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import kotlin.random.Random

class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<QueueNode<E?>>(ELIMINATION_ARRAY_SIZE)
    private val poppedNode = QueueNode<E?>(null, NodeState.POPPED)

    /*
    Queue node states:
        1. null - free to write cell
        2. QueueNode(null, POPPED) - cell state after pop()
        3. QueueNode(x, PUSHED) - cell state after push(x)
     */
    private enum class NodeState { POPPED, PUSHED }

    private class QueueNode<E>(var element: E?, var state: NodeState)

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        val randInd = Random.nextInt(ELIMINATION_ARRAY_SIZE)
        val positionReference = eliminationArray[randInd]
        val node = QueueNode<E?>(x, NodeState.PUSHED)
        if (positionReference.compareAndSet(null, node)) {
            for (j in 0 until WAIT_ITERATIONS) {
                if (positionReference.compareAndSet(poppedNode, null)) {
                    return
                }
            }
            if (!positionReference.compareAndSet(node, null)) {
                return
            }
        }

        while (true) {
            val currentTop = top.value
            if (top.compareAndSet(currentTop, Node(x, currentTop))) {
                return
            }
        }
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
        val randInd = Random.nextInt(ELIMINATION_ARRAY_SIZE)
        val el = eliminationArray[randInd].getAndSet(poppedNode)
        if (el?.element != null) {
            return el.element
        } else {
            eliminationArray[randInd].compareAndSet(poppedNode, null)
        }

        while (true) {
            val currentTop = top.value ?: return null
            if (top.compareAndSet(currentTop, currentTop.next)) {
                return currentTop.x
            }
        }
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT
private const val WAIT_ITERATIONS = 10