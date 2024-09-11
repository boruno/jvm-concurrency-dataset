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
        if (putInArrayAndWait(x)) {
            while (true) {
                val curTop: Node<E>? = top.value
                val newTop: Node<E> = Node(x, curTop)
                if (top.compareAndSet(curTop, newTop)) {
                    return
                }
            }
        }
    }

    private fun putInArrayAndWait(x: E): Boolean {
        val nodeToPut: Node<E> = Node(x, null);
        var index: Int = 0
        var waitingTime: Int = 10
//        while (true) {
//            if (eliminationArray[index].compareAndSet(null, nodeToPut)) {
//                break
//            } else {
//                index = (index + 1) % ELIMINATION_ARRAY_SIZE
//            }
//        }
        for (i in 0..ELIMINATION_ARRAY_SIZE - 1) {
            if (eliminationArray[i].compareAndSet(null, nodeToPut)) {
                index = i
                break
            }
        }
        if (index == -1) {
            return true
        }
        while(waitingTime != 0) {
            if (eliminationArray[index].value == DONE) {
                return false
            }
            waitingTime -= 1
        }
        return eliminationArray[index].compareAndSet(nodeToPut, null)
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
        for (i in 0..ELIMINATION_ARRAY_SIZE - 1) {
            if (eliminationArray[i].value is Done || eliminationArray[i].value == null) {
                continue
            }
            val cur: Node<E> = eliminationArray[i].value as Node<E>
            if (eliminationArray[i].compareAndSet(cur, DONE)) {
                return cur.x
            }
        }

        while (true) {
            val curTop: Node<E> = top.value ?: return null
            val newTop: Node<E>? = curTop.next
            if (top.compareAndSet(curTop, newTop)) {
                return curTop.x
            }
        }
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT

private enum class Done {DONE}

private val DONE = Done.DONE