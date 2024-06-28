package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import kotlin.random.Random

class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<E?>(ELIMINATION_ARRAY_SIZE)

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        while (true) {
            val curTop = top.value
            val newTop = Node(x, curTop)
            if (top.compareAndSet(curTop, newTop)) {
                return
            } else {
                var executions = 0
                while (executions < MAX_EXECUTIONS) {
                    val index = Random.nextInt(ELIMINATION_ARRAY_SIZE)
                    if (eliminationArray[index].compareAndSet(null, x)) {
                        while (executions < MAX_EXECUTIONS && eliminationArray[index].value != null) {
                            executions++
                        }
                        if (eliminationArray[index].compareAndSet(x, null)) {
                            continue
                        } else return
                    } else {
                        executions++
                    }
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
        while (true) {
            val curTop = top.value ?: return null
            val newTop = curTop.next
            if (top.compareAndSet(curTop, newTop)) {
                return curTop.x
            } else {
                var executions = 0
                while (executions < MAX_EXECUTIONS) {
                    val index = Random.nextInt(ELIMINATION_ARRAY_SIZE)
                    val value = eliminationArray[index].value ?: continue
                    if (eliminationArray[index].compareAndSet(value, null)) {
                        return value
                    }
                    executions++
                }
            }
        }
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT
private const val MAX_EXECUTIONS = 10