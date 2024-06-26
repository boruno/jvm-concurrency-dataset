package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import kotlin.random.Random

class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<Value<E>?>(ELIMINATION_ARRAY_SIZE)

    private fun defaultPush(x: E) {
        while (true) {
            val curTop = top.value
            val newTop = Node(x, curTop)
            if (top.compareAndSet(curTop, newTop))
                return
        }
    }

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        val idx: Int = Random.nextInt()
        val eliminatedX: Value<E>? = eliminationArray[idx].value
        if (eliminatedX == null) {
            if (eliminationArray[idx].compareAndSet(null, Value(x))) {
                repeat(TIME_WAITING) {}
                if (eliminationArray[idx].compareAndSet(Value(x), null)) {
                    return defaultPush(x)
                }
            } else {
                return defaultPush(x)
            }
        } else {
            return defaultPush(x)
        }
    }

    private fun defaultPop(): E? {
        while (true) {
            val curTop = top.value ?: return null
            val newTop = curTop.next
            if (top.compareAndSet(curTop, newTop))
                return curTop.x
        }
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
        val idx: Int = Random.nextInt()
        while (true) {
//            val idx: Int = Random.nextInt()
            val eliminatedX: Value<E> = eliminationArray[idx].value ?: return defaultPop()
            if (eliminationArray[idx].compareAndSet(eliminatedX, null))
                return eliminatedX.x
        }
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT

private const val TIME_WAITING = 100

private class Value<E>(val x: E)
