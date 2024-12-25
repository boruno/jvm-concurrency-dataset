//package day1

import kotlinx.atomicfu.*

class MSQueue<T> : Queue<T> {
    private val head: AtomicRef<Node<T>>
    private val tail: AtomicRef<Node<T>>

    init {
        val dummy = Node<T>(null)
        head = atomic(dummy)
        tail = atomic(dummy)
    }

    override fun enqueue(element: T) {
        while (true) {
            val node = Node(element)
            val zeroNode = Node(null) as Node<T>?
            val curTail  = tail
            if (curTail.value.next.compareAndSet(null, node)){ // bad thing
                tail.compareAndSet(curTail.value, node)
                return
            }
            else{
               tail.compareAndSet(curTail.value, curTail.value.next.value !!) // ???? idea did it by herself ???
            }

        }
        //TODO("implement me")
    }

    override fun dequeue(): T? {
        var counter = 0
        while (true) {
            val curHead = head
            val curHeadNext = curHead.value.next
            require(curHeadNext.value != null) { "Empty queue!" }
            if (head.compareAndSet(curHead.value, curHeadNext.value !!)) { // may be bad thing
                return curHeadNext.value !!.element // may be bad thing
            }
            if (counter > 50)
                return null
            counter++
        }
        //TODO("implement me")
    }

    private class Node<E>(
        var element: E?
    ) {
        val next = atomic<Node<E>?>(null)
    }
}
