//package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import kotlin.random.Random

class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<Operation<E>?>(ELIMINATION_ARRAY_SIZE)

    private val random = Random(284447)
    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        while (true) {
            val curTop = top.value
            val newTop = Node(x, curTop)

            if (top.compareAndSet(curTop, newTop)) {
                return
            }

            val index = random.nextInt(ELIMINATION_ARRAY_SIZE)
            val operation = eliminationArray[index].value ?:
                if(!eliminationArray[index].compareAndSet(null, Operation(false, x))) return
                else {
                    while (eliminationArray[index].value?.done == false) {
                        continue
                    }

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
        while (true) {
            val curTop = top.value ?: return null

            if (top.compareAndSet(curTop, curTop.next)) {
                return curTop.x
            }

            val index = random.nextInt(ELIMINATION_ARRAY_SIZE)
            val operation = eliminationArray[index].value ?: continue

            if (!operation.done && eliminationArray[index].compareAndSet(operation, Operation(true, operation.value))) {
                return operation.value
            }
        }
    }
}

private class Node<E>(val x: E, val next: Node<E>?)
private class Operation<E>(val done: Boolean, val value: E)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT