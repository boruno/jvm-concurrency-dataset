package mpp.msqueue

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
    fun enqueue(x: E) {
        val node = Node(x)
        while (true) {
            val curTail = tail.value
            val curNext = curTail.next.value
            if (curTail === tail.value){
                if (curNext != null) {
                    tail.compareAndSet(curTail, curNext)
                } else {
                    if (curTail.next.compareAndSet(null, node)) {
                        tail.compareAndSet(curTail, node)
                        return
                    }
                }
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
            val curHead = head.value
            val curNext = curHead.next.value ?: return null
            if (head.compareAndSet(curHead, curNext)) {
                return curNext.x
            }
        }
    }

    fun isEmpty(): Boolean {
        return tail.value === head.value
    }
}

private data class Node<E>(val x: E?) {
    val next = atomic<Node<E>?>(null)
}