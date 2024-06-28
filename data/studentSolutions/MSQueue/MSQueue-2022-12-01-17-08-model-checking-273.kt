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
//        TODO("implement me")
        val new = Node(x)
        while(true){
            val oldTail = tail.value
            val nextNode = oldTail.next
            if (nextNode.compareAndSet(null, new)){
                tail.compareAndSet(oldTail, new)
                break
            } else {
                tail.compareAndSet(oldTail, nextNode.value!!) // already can't be null
            }
        }
    }

    /**
     * Retrieves the first element from the queue
     * and returns it; returns `null` if the queue
     * is empty.
     */
    fun dequeue(): E? {
//        TODO("implement me")
        while (true){
            val oldHead = head.value
            val oldTail = tail.value
            val headNext = oldHead.next.value
            if (oldHead == oldTail){
                if (headNext != null){
                    tail.compareAndSet(oldTail, headNext)
                } else {
                    return null
                }
            } else {
                if (head.compareAndSet(oldHead, headNext!!)){ // headNext is null only when oldHead == oldTail
                    return oldHead.x
                }
            }
        }
    }

    fun isEmpty(): Boolean {
//        TODO("implement me")
        val oldHead = head.value
        val oldTail = tail.value
        return oldHead == oldTail && oldHead.x == null
    }
}

private class Node<E>(val x: E?) {
    val next = atomic<Node<E>?>(null)
}