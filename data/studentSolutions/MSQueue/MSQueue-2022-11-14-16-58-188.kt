//package mpp.msqueue

import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic

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
    fun enqueue(x: E)
    {
        var newTail = Node(x);
        tail.value.next.value = newTail; //Добавление новой вершины в очередь
        tail.value = tail.value.next.value as Node<E>;// Изменение хвоста списка
    }

    /**
     * Retrieves the first element from the queue
     * and returns it; returns `null` if the queue
     * is empty.
     */
    fun dequeue(): E?
    {
        if (head.value.next.value == null)
            return null;
        head.value = head.value.next.value as Node<E>;
        return head.value.x; //H - новый фиктивный элемент
    }

    fun isEmpty(): Boolean {
        return head.value.next.value == null;
    }
}

private class Node<E>(var x: E?) {
    val next = atomic<Node<E>?>(null)
}