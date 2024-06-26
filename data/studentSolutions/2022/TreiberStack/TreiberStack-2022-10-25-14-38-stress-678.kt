package mpp.stack

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import kotlinx.atomicfu.loop
import kotlin.random.Random

class TreiberStack<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<E?>(ELIMINATION_ARRAY_SIZE)
    /**
     * Adds the specified element [x] to the stack.
     */


    fun push(x: E) {
        var pos = Random.nextInt(ELIMINATION_ARRAY_SIZE)
        var tries = ELIMINATION_ARRAY_SIZE
        while(tries-- > 0) {
            if (eliminationArray[pos].compareAndSet(null, x)) {
                var flag = true
                for (i in 1..100) {
                    if (eliminationArray[pos].value != x) {
                        flag = false
                        break
                    }
                }
                if (flag){
                    if (eliminationArray[pos].compareAndSet(x, null))
                        pushToStack(x)
                }
                return
            }
            pos++
            pos %= ELIMINATION_ARRAY_SIZE
        }
        pushToStack(x)
    }

    private fun pushToStack(x : E) {
        top.loop { cur ->
            val newNode = Node(x, cur)
            if (top.compareAndSet(cur, newNode)) return
        }
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
        val pos = Random.nextInt(ELIMINATION_ARRAY_SIZE)
        eliminationArray[pos].loop { cur ->
            if (cur == null)
                return popFromStack()
            if (eliminationArray[pos].compareAndSet(cur, null))
                return cur
        }
    }

    private fun popFromStack() : E? {
        top.loop { cur ->
            if (cur == null)
                return null
            if (top.compareAndSet(cur, cur.next))
                return cur.x
        }
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT