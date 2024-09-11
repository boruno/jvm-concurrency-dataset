package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls

class TreiberStackWithElimination<E> {

    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<Any?>(ELIMINATION_ARRAY_SIZE)
    private val stolen = -1

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        while (true) {
            var cur_top = top.value
            var new_top = if (cur_top == null) Node(x, null)  else Node(x, cur_top)

            //eliminate секция
            for (i in 0 until ELIMINATION_ARRAY_SIZE) {
                if (eliminationArray[i].value == null) {
                    if (eliminationArray[i].compareAndSet(null, new_top)) {
                        for (j in 1..ELIMINATION_WAIT_ITERATIONS) {
                            if (eliminationArray[i].compareAndSet(-1, null)) {
                                return
                            }
                        }
                        if (eliminationArray[i].compareAndSet(new_top, null)) {
                            break
                        } else {
                            eliminationArray[i].compareAndSet(-1, null)
                        }
                    }
                }

            }
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
        while (true) {
            val cur_top = top.value;
            if (cur_top == null) {
                return null
            }

            for (i in 0 until ELIMINATION_ARRAY_SIZE) {
                var value = eliminationArray[i].value
                if (value != null && value != stolen && eliminationArray[i].compareAndSet(value, stolen)) {
                    value = value as Node<E>
                    return value.x
                }
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