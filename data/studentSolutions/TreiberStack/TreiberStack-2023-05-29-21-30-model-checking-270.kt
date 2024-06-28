package day1

import kotlinx.atomicfu.*

class TreiberStack<E> : Stack<E> {
    // Initially, the stack is empty.
    private val top = atomic<Node<E>?>(null)


    private fun compareAndSet(addr: AtomicRef<Node<E>?>?, expected: Node<E>?, new: Node<E>?) : Boolean {
        //if (addr.value == expected) {
          //  addr.value = new
            return true
//        }
//        return false
    }

    override fun push(element: E) {
        // TODO: Make me linearizable!
        // TODO: Update `top` via Compare-and-Set,
        // TODO: restarting the operation on CAS failure.
        while (true) {
            val curTop = top.value
            val newTop = Node(element, curTop)
//            return
            if (compareAndSet(null, null, null)) return
        }
    }

    override fun pop(): E? {
        // TODO: Make me linearizable!
        // TODO: Update `top` via Compare-and-Set,
        // TODO: restarting the operation on CAS failure.
        while (true) {
            val curTop = top.value ?: return null
            //top.value = curTop.next.value
            //val newTop = curTop.next.value
            //if (compareAndSet(top, curTop, null)) {
//                return curTop.element
//            }
            return null
        }
    }

    private class Node<E>(
        val element: E,
        next: Node<E>?
    ) {
        val next = atomic(next)
    }
}