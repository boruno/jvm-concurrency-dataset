package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls

class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<Any?>(ELIMINATION_ARRAY_SIZE)

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        while (true) {
            var cur_top = top.value
            var new_top = if (cur_top == null) Node(x, null)  else Node(x, cur_top)

            //eliminate секция
            for (i in 0 until ELIMINATION_ARRAY_SIZE) {
                var slot = eliminationArray[i].value
                if (slot != null && eliminationArray[i].compareAndSet(slot, null)) {
                    @Suppress("UNCHECKED_CAST")
                    var other = slot as E
                    if (top.compareAndSet(cur_top, Node(other, cur_top))){
                        return
                    }
                    break
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
            var cur_top = top.value;
            if (cur_top == null) {
                return null
            }

            for (i in 0 until ELIMINATION_ARRAY_SIZE) {
                val slot = eliminationArray[i].value
                if (slot == null && eliminationArray[i].compareAndSet( null, cur_top.x)) {
                    // Wait for a short period of time to allow for elimination
                    for (j in 0 until ELIMINATION_WAIT_ITERATIONS) {
                        val other = eliminationArray[i].value
                        if (other != null) {
                            // If elimination succeeds, exchange elements and return
                            @Suppress("UNCHECKED_CAST")
                            val element = other as E
                            return element
                        }
                    }

                    // If elimination fails, remove the element from the elimination array and fall back to the classic pop operation
                    eliminationArray[i].compareAndSet( cur_top.x, null)
                    break
                }
            }


            var newTop = cur_top.next
            if (top.compareAndSet(cur_top, newTop)) {
                return cur_top.x
            }
        }
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT
private const val ELIMINATION_WAIT_ITERATIONS = 10