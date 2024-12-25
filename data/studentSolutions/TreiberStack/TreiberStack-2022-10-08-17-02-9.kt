//package mpp.stack

import kotlinx.atomicfu.atomic

class TreiberStack<E> {
    private val top = atomic<Node<E>?>(null)

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        while(true) {
            if (top.value == null) {
                if(top.compareAndSet(null, Node<E>(x, null)))
                    return;
            }
            else
            {
                val oldTop = top.value;
                val newTop = Node<E>(x, oldTop);
                if (top.compareAndSet(oldTop, newTop))
                    return;
            }
        }
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
        if (top.value == null)
        {
            return null;
        }
        while(true)
        {
            val oldTop = top.value;
            val result = oldTop!!.x;
            val next = oldTop.next;
            if(top.compareAndSet(oldTop, next))
            {
                return result;
            }
        }
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT