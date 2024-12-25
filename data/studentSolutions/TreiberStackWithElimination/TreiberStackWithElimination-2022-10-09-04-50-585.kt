//package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import kotlin.random.Random

/**
 * @author Viktor Panasyuk
 */
class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<Any?>(ELIMINATION_ARRAY_SIZE)

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        var curIndexTry = 0
        while (curIndexTry < ELIMINATION_TRIES_COUNT) {
            val randomIndex = Random.nextInt(0, eliminationArray.size)
            if (eliminationArray[randomIndex].compareAndSet(null, x)) {
                var curValueTry = 0
                while (curValueTry < ELIMINATION_TRIES_COUNT) {
                    if (eliminationArray[randomIndex].compareAndSet(null, null)) {
                        return
                    }
                    curValueTry++
                }

                eliminationArray[randomIndex].compareAndSet(x, null)
                return defaultPush(x)
            }
            curIndexTry++
        }

        return defaultPush(x)
    }

    private fun defaultPush(x: E) {
        while (true) {
            val head = top.value
            val newHead = Node(x, head)
            if (top.compareAndSet(head, newHead)) {
                return
            }
        }
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    @Suppress("UNCHECKED_CAST")
    fun pop(): E? {
        var curIndexTry = 0
        while (curIndexTry < ELIMINATION_TRIES_COUNT) {
            val randomIndex = Random.nextInt(0, eliminationArray.size)
            var curElementTry = 0
            while (curElementTry < ELIMINATION_TRIES_COUNT) {
                val element = eliminationArray[randomIndex].getAndSet(null)
                if (element != null) {
                    return element as E
                }
                curElementTry++
            }
            curIndexTry++
        }

        return defaultPop()
    }

    private fun defaultPop(): E? {
        while (true) {
            val head = top.value
            if (head != null) {
                if (top.compareAndSet(head, head.next)) {
                    return head.value;
                }
            } else {
                return null
            }
        }
    }
}

private class Node<E>(val value: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT
private const val ELIMINATION_TRIES_COUNT = 10