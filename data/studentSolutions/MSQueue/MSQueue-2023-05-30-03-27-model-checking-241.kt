package day1

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
            val curTail : Node<T> = tail.value
            if (curTail.next.compareAndSet(Node(null), node)){
                tail.compareAndSet(curTail, node)
                return
            }
            else{
                curTail.next.value?.let { tail.compareAndSet(curTail, it) } // ???? idea did it by herself ???
            }
        }
        //TODO("implement me")
    }

    override fun dequeue(): T? {
        while (true){
            val curHead = head
            val curHeadNext = curHead.value.next
            require(curHeadNext.value != null) {"Empty queue!"}
            if (head.compareAndSet(curHead.value, curHeadNext.value !!)) { // may be bad thing
                return curHeadNext.value!!.element // may be bad thing
            }
        }
        //TODO("implement me")
    }

    private class Node<E>(
        var element: E?
    ) {
        val next = atomic<Node<E>?>(null)
    }
}
