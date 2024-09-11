package mpp.stack

import kotlinx.atomicfu.atomic

class TreiberStack<E> {
    private val top = atomic<Node<E>?>(null)

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        var newHead: Node<E>
        var oldHead: Node<E>?
        do {
            oldHead = top.value
            newHead = Node(x, top.value);
        } while (!top.compareAndSet(oldHead, newHead));
//        while (true) {
//            val newHead = Node(x, top.value)
//            if (cas(top.value, newHead)) {
//                break
//            }
//        }
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
//        while (true) {
//            val head = top.value?.x
//            if (cas(top.value, top.value?.next)) {
//                return head
//            }
//        }
        var newHead: Node<E>?
        var oldHead: Node<E>?
        do {
            oldHead = top.value
            if (oldHead == null)
                return null
            newHead = oldHead.next
        } while (!top.compareAndSet(oldHead, newHead));
        return oldHead?.x;
    }

//    private fun cas(old : Node<E>?, new : Node<E>?) : Boolean {
//        if (top.value != old) {
//            return false
//        }
//        top.lazySet(new)
//        return true
//    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT