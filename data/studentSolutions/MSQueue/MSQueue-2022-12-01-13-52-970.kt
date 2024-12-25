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
    fun enqueue(x: E) {
        while (true){
            val newNode = Node(x)
            val curTail = tail.value
            if (curTail.next.compareAndSet(null, newNode)) {
                tail.compareAndSet(curTail, newNode)
                return
            } else {
                tail.compareAndSet(curTail, curTail.next.value!!)
            }
        }

    }

    /**
     * Retrieves the first element from the queue
     * and returns it; returns `null` if the queue
     * is empty.
     */
    fun dequeue(): E? {
        while (true){
            val curHead = head
            if (head.value.next.value == null){
                return null
            }
            val newHead = curHead.value.next
            if (head.compareAndSet(curHead.value, newHead.value!!)){
                return curHead.value.x
            }
        }
    }

    fun isEmpty(): Boolean {
        val curHead = head
        if (curHead.value.x == null){
            return true
        }
        return false
    }
}

private class Node<E>(val x: E?) {
    val next = atomic<Node<E>?>(null)
}

fun main(args : Array<String>){
    val q = MSQueue<Int>();
    println(q.enqueue(-8))
    println(q.dequeue())
}