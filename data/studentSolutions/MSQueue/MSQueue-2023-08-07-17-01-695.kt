//package day1

import java.util.concurrent.atomic.AtomicReference

class MSQueue<E> : Queue<E> {
    private val head: AtomicReference<Node<E>>
    private val tail: AtomicReference<Node<E>>

    init {
        val dummy = Node<E>(null)
        head = AtomicReference(dummy)
        tail = AtomicReference(dummy)
    }

    override fun enqueue(element: E) {
        while(true) {
            val newNode = Node(element)
            val currentTail = tail
            val next = currentTail.get().next
            // update the next pointer
            if(next.get()!!.next.compareAndSet(null, newNode)) {
                // update the tail pointer
                tail.set(newNode)
                return
            }
        }
    }

    // should only care about the head
    override fun dequeue(): E? {
        while(true) {
            val curHead = head.get()
            val curHeadValue = curHead.next.get()
            if(curHeadValue == null) {
                // dummy points to nothing -> list empty
                return null
            }
            // we have a value
            val returnValue = curHeadValue.element
            // move the head pointer
            if(head.compareAndSet(curHead, curHeadValue)) {
                // we successfully moved it
                return returnValue
            }
        }
    }

    // FOR TEST PURPOSE, DO NOT CHANGE IT.
    override fun validate() {
        check(tail.get().next.get() == null) {
            "`tail.next` must be `null`"
        }
    }

    private class Node<E>(
        var element: E?
    ) {
        val next = AtomicReference<Node<E>?>(null)
    }
}
