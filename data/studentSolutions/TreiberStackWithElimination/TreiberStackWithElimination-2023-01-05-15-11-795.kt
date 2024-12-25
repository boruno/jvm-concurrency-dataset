//package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import kotlin.random.Random

class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<Any?>(ELIMINATION_ARRAY_SIZE)
    private val done = "done"

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        while(true) {
            val ind = Random.nextInt(0, ELIMINATION_ARRAY_SIZE)
            if(eliminationArray[ind].compareAndSet(null, x)) {
                for(j in 0..100) {
                    if (eliminationArray[ind].value === done) {
                        eliminationArray[ind].value = null
                        return
                    }
                }
                if(!eliminationArray[ind].compareAndSet(x, null)) {
                    eliminationArray[ind].value = null
                    return
                }
            }

            val curTop = top.value
            val newTop = Node(x, curTop)
            if(top.compareAndSet(curTop, newTop))
                return
        }
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
        while(true) {
            val ind = Random.nextInt(0, ELIMINATION_ARRAY_SIZE)
            val x = eliminationArray[ind].value
            if (x != null && x !== done && eliminationArray[ind].compareAndSet(x, done)) {
                return x as E
            }
            val curTop = top.value ?: return null
            val newTop = curTop.next
            if(top.compareAndSet(curTop, newTop))
                return curTop.x
        }
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT