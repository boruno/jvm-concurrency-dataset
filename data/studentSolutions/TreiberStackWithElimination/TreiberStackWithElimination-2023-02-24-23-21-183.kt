//package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import kotlin.random.Random

class TreiberStackWithElimination<E> {

    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<Any?>(ELIMINATION_ARRAY_SIZE)
    private val stolen = "Done"

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {

        val value = x

        for (i in 0 until ELIMINATION_ARRAY_SIZE) {
            val rnd = Random.nextInt(ELIMINATION_ARRAY_SIZE)
            if (eliminationArray[rnd].compareAndSet(null, value)) {
                for (j in 0 until ELIMINATION_WAIT_ITERATIONS) {
                    if (eliminationArray[rnd].compareAndSet(stolen, null)) {
                        return
                    }
                }
            }
            if (eliminationArray[rnd].compareAndSet(value, null)){
                break
            } else {
                eliminationArray[rnd].compareAndSet(stolen, null)
                return
            }
        }

        while (true) {
            var cur_top = top.value
            var new_top = if (cur_top == null) Node(x, null)  else Node(x, cur_top)

            // основная часть
            if (top.compareAndSet(cur_top, new_top)) {
                return;
            }
        }
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
        for (i in 0 until ELIMINATION_ARRAY_SIZE) {
            var rndI = Random.nextInt(ELIMINATION_ARRAY_SIZE)
            val value = eliminationArray[rndI].value
            if (value != null && value != stolen && eliminationArray[i].compareAndSet(value, stolen)) {
                return value as E
            }
        }
        while (true) {
            val cur_top = top.value;
            if (cur_top == null) {
                return null
            }

            val newTop = cur_top.next
            if (top.compareAndSet(cur_top, newTop)) {
                return cur_top.x
            }
        }
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT

private const val ELIMINATION_WAIT_ITERATIONS = 10