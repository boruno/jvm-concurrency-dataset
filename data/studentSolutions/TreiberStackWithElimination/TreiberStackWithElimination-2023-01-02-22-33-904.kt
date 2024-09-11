package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.concurrent.ThreadLocalRandom

class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<Any?>(ELIMINATION_ARRAY_SIZE)

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        var cnt = 0
        var shouldGenInd = true
        var ind = 0
        while (cnt < 100) {
            cnt += 1
            if (shouldGenInd) {
                ind = ThreadLocalRandom.current().nextInt(ELIMINATION_ARRAY_SIZE)
                if (!eliminationArray[ind].compareAndSet(null, x)) {
                    continue
                }
            }
            shouldGenInd = false
            if (eliminationArray[ind].compareAndSet(DONE, null)) {
                return
            }
        }
        if (!eliminationArray[ind].compareAndSet(x, null)) {
            if (eliminationArray[ind].compareAndSet(DONE, null)) {
                return
            }
        }
        while (true) {
            val curTop = top.value
            val newTop = Node(x, curTop)
            if (top.compareAndSet(curTop, newTop)) {
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
        var cnt = 0
        while (cnt < 100) {
            cnt += 1
            val ind = ThreadLocalRandom.current().nextInt(ELIMINATION_ARRAY_SIZE)
            val value = eliminationArray[ind].value ?: continue
            if (value == DONE) {
                continue
            }
            if (eliminationArray[ind].compareAndSet(value, DONE)) {
                return value as? E
            }
        }
        while (true) {
            val curTop = top.value
            val newTop = curTop?.next
            if (top.compareAndSet(curTop, newTop)) {
                return curTop?.x
            }
        }
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT
private val DONE = Any()
