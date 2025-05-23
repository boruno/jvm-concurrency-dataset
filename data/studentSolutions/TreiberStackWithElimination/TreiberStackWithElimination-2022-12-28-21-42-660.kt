//package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls

class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<Any?>(ELIMINATION_ARRAY_SIZE)

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
                val index = Thread.currentThread().id.toInt() and (ELIMINATION_ARRAY_SIZE - 1)
                val node = Node(x, old)
                eliminationArray[index].getAndSet(node)
                Thread.yield()
                if (eliminationArray[index].compareAndSet(node, null)) {
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
        var old: Node<E>?
        var node: Node<E>?
        while (true) {
            old = top.value
            if (old == null) {
                return null
            }
            node = old.next
            if (top.compareAndSet(old, node)) {
                return old.x
            } else {
                val index = Thread.currentThread().id.toInt() and (ELIMINATION_ARRAY_SIZE - 1)
                eliminationArray[index].getAndSet(old)
                Thread.yield()
                if (eliminationArray[index].compareAndSet(old, null)) {
                    return null
                }
            }
        }
//        return old?.x
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT