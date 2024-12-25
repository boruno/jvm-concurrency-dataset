//package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import kotlin.random.Random

class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<E?>(ELIMINATION_ARRAY_SIZE)

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        while (true) {
            val ind = Random.nextInt(0, eliminationArray.size)
            val item = eliminationArray[ind].value
            if (item != null) {
                trivialPush(x);
                return;
            }
            if (eliminationArray[ind].compareAndSet(null, x)) {
                var count = 0;
                while (true) {
                    if (count == 10 && eliminationArray[ind].compareAndSet(x, null)) {
                        trivialPush(x);
                        return;
                    }
                    if (count == 10 || eliminationArray[ind].value != x) {
                        return;
                    }
                    count++;
                }
            }
        }
    }

    private fun trivialPush(x : E) {
        while (true) {
            val node = Node(x, top.value)
            if (top.compareAndSet(node.next, node)) {
                return
            }
        }
    }

    private fun trivialPop() : E? {
        while (true) {
            val node = top.value ?: return null

            if (top.compareAndSet(node, node.next)) {
                return node.item
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
            val ind = Random.nextInt(0, eliminationArray.size)
            val x = eliminationArray[ind].value ?: return trivialPop();
            if (eliminationArray[ind].compareAndSet(x, null)) {
                return x;
            }
        }
    }
}

private class Node<E>(val item: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT