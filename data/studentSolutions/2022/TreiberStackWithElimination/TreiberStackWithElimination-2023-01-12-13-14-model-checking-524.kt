package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.lang.Integer.*
import kotlin.random.Random

class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<Node<E>?>(ELIMINATION_ARRAY_SIZE)

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        val rmpRand = Random.nextInt(0, ELIMINATION_ARRAY_SIZE)
        val right = min(rmpRand + 1, ELIMINATION_ARRAY_SIZE - 1)
        val left = max( rmpRand - 1, 0)

        val nodeToPush = Node(x, null)
        for (num in left until right + 1) {
            if (!eliminationArray[num].compareAndSet(null, nodeToPush)) {
                continue;
            }

            var ptr = 0
            while (ptr < 200) {
                val curNode = eliminationArray[num].value
                if (curNode != null) {
                    if (curNode.x == MAX_VALUE) {
                        eliminationArray[num].getAndSet(null)
                        return
                    }
                    ptr++
                }
                if (!eliminationArray[num].compareAndSet(nodeToPush, null)) {
                    eliminationArray[num].getAndSet(null)
                    return
                } else {
                    break;
                }
            }
            while (true) {
                val current = top.value
                val update = Node(x, current)
                if (top.compareAndSet(current, update)) {
                    return
                }
            }
        }
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
        val rmpRand = Random.nextInt(0, ELIMINATION_ARRAY_SIZE)
        val right = min(rmpRand + 1, ELIMINATION_ARRAY_SIZE - 1)
        val left = max( rmpRand - 1, 0)
        val newElem = Node(MAX_VALUE as E, null)
        for (num in left until right + 1) {
            val elem = eliminationArray[num].value
            if (elem != null && elem.x != MAX_VALUE) {
                if (eliminationArray[num].compareAndSet(elem, newElem)) {
                    return elem.x
                }
            }
        }

        while (true) {
            val cur = top.value
            if (cur != null) {
                if (top.compareAndSet(cur, cur.next)) {
                    return cur.x
                }
            } else {
                return null
            }
        }
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT