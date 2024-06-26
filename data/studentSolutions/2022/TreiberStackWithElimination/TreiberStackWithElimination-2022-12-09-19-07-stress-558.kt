package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import kotlin.random.Random

class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<Pair<E?, Operation>>(ELIMINATION_ARRAY_SIZE)

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        while (true) {
            val topSnapshot = top.value
            val node = Node(x, topSnapshot)
            if (!top.compareAndSet(topSnapshot, node)) {
                val index = Random.nextInt(0, ELIMINATION_ARRAY_SIZE - 1)
                if (eliminationArray[index].value == null) {
                    val el = Pair(x, Operation.PUSH);
                    eliminationArray[index].compareAndSet(null, el)
                    break
                } else if (eliminationArray[index].value?.second == Operation.POP) {
                    eliminationArray[index].compareAndSet(eliminationArray[index].value, null)
                } else {
                    continue;
                }
            } else {
                break;
            }
        }
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
        while (true) {
            val topSnapshot = top.value ?: return null
            if (!top.compareAndSet(topSnapshot, topSnapshot.next)) {
                val index = Random.nextInt(0, ELIMINATION_ARRAY_SIZE - 1)
                if (eliminationArray[index].value == null) {
                    val el = Pair(null, Operation.POP);
                    eliminationArray[index].compareAndSet(null, el)
                    continue
                } else if (eliminationArray[index].value?.second == Operation.PUSH) {
                    eliminationArray[index].compareAndSet(eliminationArray[index].value, null)
                    return null
                } else {
                    continue;
                }
            } else {
                return topSnapshot.x
            }
        }
    }
}

private enum class Operation {
    PUSH, POP
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT