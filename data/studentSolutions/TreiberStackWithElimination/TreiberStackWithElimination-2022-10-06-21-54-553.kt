package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.EmptyStackException

class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<Any?>(ELIMINATION_ARRAY_SIZE)
    private val IT = 300
    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        for (i in 0 until ELIMINATION_ARRAY_SIZE) {
            if (eliminationArray[i].compareAndSet(null, x)) {
                for (it in 0..IT) {
                    if (!eliminationArray[i].equals(x)){
                        return
                    }
                }
                if (eliminationArray[i].compareAndSet(x, null)) {
                    break
                } else {
                    return
                }
            }
        }
        forcePush(x)
    }
    private fun forcePush(x: E) {
        while (true) {
            val curTop = top.value
            val newNode = Node<E>(x, curTop)
            if (top.compareAndSet(curTop, newNode)) {
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
        for (it in 0..IT) {
            for (i in 0 until ELIMINATION_ARRAY_SIZE) {
                val ans = eliminationArray[i].getAndSet(null)
                if (ans != null) {
                    return ans as E
                }
            }
        }
        return forcePop()
    }
    private fun forcePop(): E?{
        while(true) {
            val curTop = top.value ?: return null
            val newTop = curTop.next
            if (top.compareAndSet(curTop, newTop)){
                return curTop.x
            }
        }
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT