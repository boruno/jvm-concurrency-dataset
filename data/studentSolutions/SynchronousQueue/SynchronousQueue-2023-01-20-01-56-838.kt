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
    private val head: AtomicRef<Node<E>>
    private val tail: AtomicRef<Node<E>>

    init {
        val dummy = Node<E>(null, false, null)
        head = atomic(dummy)
        tail = atomic(dummy)
    }

    /**
     * Sends the specified [element] to this channel, suspending if there is no waiting
     * [receive] invocation on this channel.
     */
    suspend fun send(element: E) {
        while (true) {
            val currentTail = tail.value
            val currentHead = head.value
            val value = if (currentHead === currentTail || currentTail.isSender) {
                enqueueAndSuspend(currentTail, element)
            } else {
                dequeueAndResume(currentHead, element)
            }
            if (value !== null) return
        }
    }

    /**
     * Retrieves and removes an element from this channel if there is a waiting [send] invocation on it,
     * suspends the caller if this channel is empty.
     */
    suspend fun receive(): E {
        while (true) {
            val currentTail = tail.value
            val currentHead = head.value
            val value = if (currentHead === currentTail || !currentTail.isSender) {
                enqueueAndSuspend(currentTail, null)
            } else {
                dequeueAndResume(currentHead, null)
            }
            if (value !== null) return value
        }
    }

    private suspend fun enqueueAndSuspend(currentTail: Node<E>, element: E?): E? {
        return suspendCoroutine suspendCoroutine@{ continuation ->
            val newTail = Node(element, currentTail.isSender, continuation)
            if (currentTail.next.compareAndSet(null, newTail)) {
                tail.compareAndSet(currentTail, newTail)
            } else {
                continuation.resume(null)
                return@suspendCoroutine
            }
        }
    }

    private fun dequeueAndResume(currentHead: Node<E>, element: E?): E? {
        val newHead = currentHead.next.value!!
        return if (head.compareAndSet(head.value, newHead)) {
            newHead.continuation!!.resume(element)
            newHead.element
        } else {
            return null
        }
    }
}

private class Node<E>(val element: E?, val isSender: Boolean, val continuation: Continuation<E?>?) {

    val next = atomic<Node<E>?>(null)
}