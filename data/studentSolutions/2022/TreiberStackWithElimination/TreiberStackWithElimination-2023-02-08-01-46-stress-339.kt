package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import javax.print.attribute.IntegerSyntax
import kotlin.random.Random.Default.nextInt

enum class Mark {
    TAKEN
}

class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<Any?>(ELIMINATION_ARRAY_SIZE)


    private fun backoffPush(x: E) {
        while (true) {
            val curTop = top.value
            val nextTop = Node(x, curTop)
            if (top.compareAndSet(curTop, nextTop)) {
                return
            }
        }
    }
    private fun backoffPop(): E? {
        while (true) {
            val curTop = top.value ?: return null
            if (top.compareAndSet(curTop, curTop.next)) {
                return curTop.x
            }
        }
    }

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        var p = -1
        for (t in 0 until ELIMINATION_ARRAY_SIZE) {
            if (p != -1) {
                break
            }
            val i = nextInt(ELIMINATION_ARRAY_SIZE)
            for (retries in 0 until 3) {
                if (eliminationArray[i].compareAndSet(null, x)) {
                    p = i
                }
            }
        }
        // in case we haven't found appropriate cell in the array
        if (p == -1) {
            backoffPush(x)
            return
        }
        for (retries in 0 until 3) {
            if (eliminationArray[p].compareAndSet(Mark.TAKEN, null)) {
                return
            }
        }
        // no thread was interested to take our element, bad luck, need to push it to the stack now
        backoffPush(x)
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
        for (t in 0 until ELIMINATION_ARRAY_SIZE) {
            val i = nextInt(ELIMINATION_ARRAY_SIZE)
            for (retries in 0 until 3) {
                val curElement = eliminationArray[i].value
                if (curElement == null || curElement == Mark.TAKEN) {
                    continue
                }
                if (eliminationArray[i].compareAndSet(curElement, Mark.TAKEN)) {
                    @Suppress("UNCHECKED_CAST")
                    return curElement as E
                }
            }
        }
        return backoffPop()
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT