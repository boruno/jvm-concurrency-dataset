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

    private val q = MSQueue<SendRecieve<E>>()
    /**
     * Sends the specified [element] to this channel, suspending if there is no waiting
     * [receive] invocation on this channel.
     */
    suspend fun send(element: E): Unit {
        if (q.isEmpty() || q.tail.value.x!!.element != null) {
            suspendCoroutine<Unit> { cont ->
                q.enqueue(SendRecieve(cont, null, element))
            }
        } else {
            val e = q.dequeue()
            e!!.contReceive!!.resume(element)
        }
    }

    /**
     * Retrieves and removes an element from this channel if there is a waiting [send] invocation on it,
     * suspends the caller if this channel is empty.
     */
    suspend fun receive(): E {
        if (q.isEmpty() || q.tail.value.x!!.element == null) {
            return suspendCoroutine { cont ->
                q.enqueue(SendRecieve(null, cont, null))
            }
        } else {
            val z = q.dequeue()
            z!!.contSend!!.resume(Unit)
            return z.element!!
        }
    }

    private class MSQueue<E> {
        val head: AtomicRef<Node<E>>
        val tail: AtomicRef<Node<E>>

        init {
            val dummy = Node<E>(null)
            head = atomic(dummy)
            tail = atomic(dummy)
        }


        /**
         * Adds the specified element [x] to the queue.
         */
        fun enqueue(x: E) {
            val node = Node(x)
            while (true) {
                val cur_tail = tail.value
                if (cur_tail.next.compareAndSet(null, node)) {
                    tail.compareAndSet(cur_tail, node)
                    return
                } else {
                    val vu = cur_tail.next.value;
                    if (vu != null) {
                        tail.compareAndSet(cur_tail, vu)
                    }
                }
            }
        }

        /**
         * Retrieves the first element from the queue
         * and returns it; returns `null` if the queue
         * is empty.
         */
        fun dequeue(): E? {
            while (true) {
                val cur_head = head.value
                val cur_head_next = cur_head.next.value ?: return null
                if (head.compareAndSet(cur_head, cur_head_next)) {
                    return cur_head_next.x
                }
            }
        }

        fun isEmpty(): Boolean {
            val cur_head = head.value
            val cur_head_next = cur_head.next.value
            return cur_head_next == null
        }
    }

    private class Node<E>(val x: E?) {
        val next = atomic<Node<E>?>(null)
    }

    private class SendRecieve<E>(val contSend: Continuation<Unit>?, val contReceive: Continuation<E>?, val element : E?)
}