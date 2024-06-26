package mpp.msqueue

import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import java.lang.Exception

class MSQueue<E> {
    private val head: AtomicRef<Node<E>>
    private val tail: AtomicRef<Node<E>>

    init {
        val dummy = Node<E>(null)
        head = atomic(dummy)
        tail = atomic(dummy)
    }

    /**
     * Adds the specified element [x] to the queue.
     */
    fun enqueue(x: E) {
        while (true) {
            var node = Node(x);
            var cur_tail = this.tail.value;
            if (cur_tail.next.compareAndSet(null, node)) {
                tail.compareAndSet(cur_tail, node);
                return;
            }
            else {
                cur_tail.next.value?.let { tail.compareAndSet(cur_tail, it) }
            }
        }
    }

    /**
     * Retrieves the first element from the queue
     * and returns it; returns `null` if the queue
     * is empty.
     */
    fun dequeue(): E? {
        while (true) {
            var cur_head = this.head.value;
            var cur_head_next = cur_head.next;
            if (cur_head_next.value == null) {
                throw Exception("Empty queue exception");
            }
            var cur_head_next_value = cur_head_next.value;
            if (cur_head_next_value != null) {
                if (this.head.compareAndSet(cur_head, cur_head_next_value)) {
                    return cur_head_next_value.x;
                }
            }
        }
    }

    fun isEmpty(): Boolean {
        if (this.head.value.next.value == null) {
            return true;
        }
        return false;
    }
}

private class Node<E>(val x: E?) {
    val next = atomic<Node<E>?>(null)
}