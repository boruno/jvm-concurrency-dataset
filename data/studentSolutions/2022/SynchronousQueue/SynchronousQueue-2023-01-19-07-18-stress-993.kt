import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * An element is transferred from sender to receiver only when [send] and [receive]
 * invocations meet in time (rendezvous), so [send] suspends until another coroutine
 * invokes [receive] and [receive] suspends until another coroutine invokes [send].
 */
class SynchronousQueue<E> {
    private val head: AtomicRef<Node>
    private val tail: AtomicRef<Node>

    inner class Node(val element: E?, val cont: Continuation<Any?>?) {
        val next = atomic<Node?>(null)
    }

    private object RETRY


    init {
        val dummy = Node(null, null)
        head = atomic(dummy)
        tail = atomic(dummy)
    }

    /**
     * Sends the specified [element] to this channel, suspending if there is no waiting
     * [receive] invocation on this channel.
     */
    suspend fun send(element: E) {
        while (true) {
            val result = check(head.value, tail.value, element)
            if (result != RETRY) {
                break
            }
        }
    }

    /**
     * Retrieves and removes an element from this channel if there is a waiting [send] invocation on it,
     * suspends the caller if this channel is empty.
     */
    suspend fun receive(): E {
        while (true) {
            val result = check(head.value, tail.value, null)
            if (result != RETRY) {
                return result as E
            }
        }
    }

    private suspend fun check(
        head: Node,
        tail: Node,
        element: E?
    ): Any? {
        return if (head == tail || tail.element == null) {
            enqueue(tail, element)
        } else {
            dequeue(head, element)
        }
    }

    private fun dequeue(head: Node, element: E?): Any? {
        val new = head.next.value!!
        return if (this.head.compareAndSet(head, new)) {
            new.cont!!.resume(element)
            new.element
        } else {
            RETRY
        }
    }

    private suspend fun enqueue(tail: Node, element: E?): Any? {
        return suspendCoroutine { cont ->
            val new = Node(element, cont)
            val flag = !tail.next.compareAndSet(null, new)
            this.tail.compareAndSet(tail, tail.next.value!!)
            if (flag) {
                cont.resume(RETRY)
            }
        }
    }
}
