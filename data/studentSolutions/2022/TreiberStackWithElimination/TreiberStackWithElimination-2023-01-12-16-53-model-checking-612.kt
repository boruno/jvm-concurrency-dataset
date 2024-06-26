package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import kotlin.random.Random

class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<Any?>(ELIMINATION_ARRAY_SIZE)
    private val rand = Random(10)
    private val node = Node(null, null)

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        val ind = rand.nextInt(ELIMINATION_ARRAY_SIZE)
        val el = eliminationArray[ind]
        val n = Node(x, null)
        if (el.compareAndSet(null, n)) {
            val start = System.nanoTime();
            while (start + 1e6 < System.nanoTime()) {
                if (el.compareAndSet(node, null)) {
                    return
                }
            }

            if (!el.compareAndSet(n, null)) {
                el.value = null;
                return
            }
        }

        while (true) {
            val curTop = top.value;
            val newTop = Node(x, curTop);
            if (top.compareAndSet(curTop, newTop)) return
        }
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
        val ind = rand.nextInt(ELIMINATION_ARRAY_SIZE)
        val el = eliminationArray[ind]
        val res = el.value
        if (res != null && el.compareAndSet(res, node)) return (res as Node<E>).x

        while (true) {
            val curTop = top.value ?: return null;
            val newTop = curTop.next
            if (top.compareAndSet(curTop, newTop)) return curTop.x
        }
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT