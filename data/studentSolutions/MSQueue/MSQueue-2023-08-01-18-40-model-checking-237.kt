package day1

import kotlinx.atomicfu.*
import java.lang.Exception

class MSQueue<E> : Queue<E> {
    private val head: AtomicRef<Node<E>>
    private val tail: AtomicRef<Node<E>>

    init {
        val dummy = Node<E>(null)
        head = atomic(dummy)
        tail = atomic(dummy)
    }

    override fun enqueue(element: E) {

        val node = Node(element);

        while(true) {
            val curTail = tail;

            if (curTail.value.next.compareAndSet(null, node)) {

//                tail.value = node;
                tail.compareAndSet(curTail.value, node)
                return;
            }
            else {
//                if (curTail.value.next.value == null)
//                {
//                    throw Exception("aaa");
//
//                }
                if (curTail.value.next.value != null)
                    tail.compareAndSet(curTail.value, curTail.value.next.value!!)
            }
        }
    }

    override fun dequeue(): E? {
        while(true) {
            val curHead= head;
            val curHeadNext = curHead.value.next;
            if (curHeadNext.value == null) {
                return null;
            }

            if(head.compareAndSet(curHead.value, curHeadNext.value!!)) {
                return curHeadNext.value!!.element;
            }
        }
    }

    private class Node<E>(
        var element: E?
    ) {
        val next = atomic<Node<E>?>(null)
    }
}
