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
        while(true){
            val node = Node(x)
            val curr_tail = tail.value
            if(curr_tail.next.compareAndSet(null,node)) {
                tail.compareAndSet(curr_tail,node)
                return
            }
            else
            {
                tail.compareAndSet(curr_tail, curr_tail.next.value!!)
            }
        }
    }

    /**
     * Retrieves the first element from the queue
     * and returns it; returns `null` if the queue
     * is empty.
     */
    fun dequeue(): E? {
       while(true){
           val curr_head = head.value
           val curr_head_next = curr_head.next
           if(curr_head_next.value == null)
               return null
           if(head.compareAndSet(curr_head, curr_head_next.value!!))
               return curr_head_next.value!!.x
       }
    }

    fun isEmpty(): Boolean {
        if(tail.value.x == null)
            return true
        return false
    }
}

private class Node<E>(val x: E?) {
    val next = atomic<Node<E>?>(null)
}