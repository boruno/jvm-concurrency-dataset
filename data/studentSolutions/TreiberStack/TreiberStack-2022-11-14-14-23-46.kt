//package mpp.stack

import kotlinx.atomicfu.atomic

class TreiberStack<E> {
    private val top = atomic<Node<E>?>(null)

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        while (true)
        {
            var head = top.value;
            var newHead = Node(x, head);
            if (top.compareAndSet(head, newHead)) 
                return;
        }
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
        while(true)
        {
            var head = top.value;
            if (head == null)
                return null;
            if(top.compareAndSet(head, head?.next))
                return head?.x;
        }
    }
}

private class Node<E>(val x: E, var next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT