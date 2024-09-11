package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import kotlin.random.Random

class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)

    //    private val eliminationArray = atomicArrayOfNulls<Any?>(ELIMINATION_ARRAY_SIZE)
    private val eliminationArray = atomicArrayOfNulls<Node<E>?>(ELIMINATION_ARRAY_SIZE)

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        var old: Node<E>?
        while (true) {
            old = top.value
            if (top.compareAndSet(old, Node(x, old))) {
                break
            } else {
                val index = Thread.currentThread().id.toInt() % ELIMINATION_ARRAY_SIZE
                val node = Node(x, old)
                if (!eliminationArray[index].compareAndSet(null, node)) {
                    continue
                }
                Thread.yield()
                if (eliminationArray[index].compareAndSet(node, null)) {
                    break
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
        var old: Node<E>?
        var node: Node<E>?
        var counter = 5;
        while (true) {
            old = top.value
            if (old != null) {
                node = old.next
                if (top.compareAndSet(old, node)) {
                    return old.x
                }
            }
            if (--counter == 0) {
                return null
            }
//            val index = Thread.currentThread().id.toInt() % ELIMINATION_ARRAY_SIZE
            val index = Random.nextInt() % ELIMINATION_ARRAY_SIZE
            val eliminated = eliminationArray[index].getAndSet(null)
            if (eliminated != null) {
                return eliminated.x
            }
        }
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT