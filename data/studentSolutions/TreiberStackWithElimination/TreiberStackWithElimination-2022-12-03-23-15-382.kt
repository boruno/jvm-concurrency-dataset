//package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.lang.Thread.sleep
import kotlin.random.Random

class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<E?>(ELIMINATION_ARRAY_SIZE)

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        var index = Random.nextInt(ELIMINATION_ARRAY_SIZE)
        var step = 0
        while (step < ELIMINATION_ARRAY_SIZE){
            if (eliminationArray[index].compareAndSet(null, x)) {
                for (i in 1..100) {
                    if (eliminationArray[index].value != x) {
                        break
                    }
                }
                if (eliminationArray[index].compareAndSet(x, null)) {
                    simplePush(x)
                }
                return
            }
            index++
            index%= ELIMINATION_ARRAY_SIZE
            step+=1
        }
        simplePush(x)
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */

    fun pop(): E? {
        val index = Random.nextInt(ELIMINATION_ARRAY_SIZE)
        while (true) {
            val random = eliminationArray[index].value ?: return simplePop()
            if (eliminationArray[index].compareAndSet(random, null)) {
                return random
            }
        }
    }

    private fun simplePush(x: E) {
        while (true) {
            val head = top.value
            if (top.compareAndSet(head, Node(x, head))) {
                break
            }
        }
    }
    private fun simplePop(): E? {
        while (true) {
            val head = top.value ?: return null
            if (top.compareAndSet(head, head.next)) {
                return head.x
            }
        }
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT
private const val SLEEP_SECONDS = 100L
private const val WAIT_STEPS = 1000
