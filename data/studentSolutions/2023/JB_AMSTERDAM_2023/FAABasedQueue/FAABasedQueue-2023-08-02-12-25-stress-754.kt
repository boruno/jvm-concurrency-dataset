package day2

import day1.*
import kotlinx.atomicfu.*

// TODO: Copy the code from `FAABasedQueueSimplified`
// TODO: and implement the infinite array on a linked list
// TODO: of fixed-size segments.
class FAABasedQueue<E> : Queue<E> {

    private val head: AtomicRef<Node<E>>
    private val tail: AtomicRef<Node<E>>

    init {
        val dummy = Node<E>(null)
        head = atomic(dummy)
        tail = atomic(dummy)
    }

    override fun enqueue(element: E) {
        // TODO: Increment the counter atomically via Fetch-and-Add.
        // TODO: Use `getAndIncrement()` function for that.
//        while (true) {
//            val i = enqIdx.incrementAndGet()
//            if (infiniteArray[i].compareAndSet(null, element)) {
//                return
//            }
//        }
//        TODO("Implement me without infiniteArray")

        while (true) {
            val tailNode = tail.value
            val newTailNode = Node(element)
            if (tailNode.next.compareAndSet(null, newTailNode)) {
                tail.compareAndSet(tailNode, newTailNode)
                return
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun dequeue(): E? {
//        while (true) {
//            // TODO: Increment the counter atomically via Fetch-and-Add.
//            // TODO: Use `getAndIncrement()` function for that.
//            // TODO: Try to retrieve an element if the cell contains an
//            // TODO: element, poisoning the cell if it is empty.
//            // TODO: Atomically return the element.
//            if (deqIdx.value >= enqIdx.value) return null
//            val i = deqIdx.incrementAndGet()
//            if (!infiniteArray[i].compareAndSet(null, POISONED)) {
//                return infiniteArray[i].value as E
//            }
//        }
//        TODO("Implement me without infiniteArray")
        if (head.value.next.value == null) return null
        val item = head.value.next.value!!.element
        head.value = head.value.next.value!!
        return item
    }

    private class Node<E>(
        var element: E?
    ) {
        val next = atomic<Node<E>?>(null)
    }
}