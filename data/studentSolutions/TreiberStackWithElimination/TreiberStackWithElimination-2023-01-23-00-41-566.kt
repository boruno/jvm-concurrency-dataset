//package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import kotlin.random.Random

private const val ELIMINATION_ARRAY_SIZE = 0 // DO NOT CHANGE IT
private class Node<E>(val x: E, val next: Node<E>?)
class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<Any?>(ELIMINATION_ARRAY_SIZE)

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(value: E) {
        val rand = Random.nextInt(0, ELIMINATION_ARRAY_SIZE)
        for (i in 0 until ELIMINATION_ARRAY_SIZE) {
            val index = (rand + i) % ELIMINATION_ARRAY_SIZE
            val elemRef = eliminationArray[index]
            if (elemRef.compareAndSet(null, value)) {
                for (i in 0..10) {
                    if (elemRef.value == null) {
                        return
                    }
                }
                if (!elemRef.compareAndSet(value, null)) {
                    return
                }
                break
            }
        }

        while (true) {
            val curTop = top.value
            if (top.compareAndSet(curTop, Node(value, curTop))) {
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
        val rand = Random.nextInt(0, ELIMINATION_ARRAY_SIZE)
        for (i in 0 until ELIMINATION_ARRAY_SIZE) {
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
            if (top.compareAndSet(curTop, curTop.next)) {
                return curTop.x
            }
        }
    }
}