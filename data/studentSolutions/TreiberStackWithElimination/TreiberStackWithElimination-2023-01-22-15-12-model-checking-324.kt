package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import kotlin.random.Random

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT
private class Node<E>(val x: E, val next: Node<E>?)
class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<Any?>(ELIMINATION_ARRAY_SIZE)

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(value: E) {
        val rand = Random.nextInt(0, ELIMINATION_ARRAY_SIZE)
        for (i in 0 .. ELIMINATION_ARRAY_SIZE) {
            val index = (rand + i) % ELIMINATION_ARRAY_SIZE
            if (eliminationArray[index].compareAndSet(null, value)) {
            for (i in 0..10) {
                    if (eliminationArray[index].value == null) {
                        return
                    }
                }
            }
            if (!eliminationArray[index].compareAndSet(value, null)) {
                return
            }
            break
        }

        while (true) {
            val curTop = top.value
            val newTop = Node(value, curTop)
            if (top.compareAndSet(curTop, newTop)) {
                return
            }
        }
    }

    fun getPosition(index: Int): Int {
        return index % ELIMINATION_ARRAY_SIZE
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
        val rand = Random.nextInt(0, ELIMINATION_ARRAY_SIZE)
        for (i in 0 .. ELIMINATION_ARRAY_SIZE) {
            val index = (rand + i) % ELIMINATION_ARRAY_SIZE
            val value = eliminationArray[index].value
            if (value != null) {
                if (eliminationArray[index].compareAndSet(value, null)) {
                    return value as E
                }
            }
        }
        while (true) {
            val curTop = top.value ?: return null
            val newTop = curTop.next
            if (top.compareAndSet(curTop, newTop)) {
                return curTop.x
            }
        }
    }
}