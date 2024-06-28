import mpp.msqueue.MSQueue
import mpp.msqueue.Receive
import mpp.msqueue.Send
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * An element is transferred from sender to receiver only when [send] and [receive]
 * invocations meet in time (rendezvous), so [send] suspends until another coroutine
 * invokes [receive] and [receive] suspends until another coroutine invokes [send].
 */

class SynchronousQueue<E> {
    val queue = MSQueue<E>()

    /**
     * Sends the specified [element] to this channel, suspending if there is no waiting
     * [receive] invocation on this channel.
     */
    suspend fun send(element: E) {
        val operation = queue.head.value.operation

        if (operation is Receive<*>) {
            queue.dequeue()
            operation.continuation.resume(element)
        } else {
            suspendCoroutine { continuation ->
                queue.enqueue(Send(element, continuation as Continuation<Any?>))
            }
        }
    }

    /**
     * Retrieves and removes an element from this channel if there is a waiting [send] invocation on it,
     * suspends the caller if this channel is empty.
     */
    suspend fun receive(): E {
        val operation = queue.head.value.operation

        if (operation is Send<*>) {
            queue.dequeue()
            operation.continuation.resume(Unit)
            return operation.element as E
        } else {
            return suspendCoroutine { continuation ->
                queue.enqueue(Receive(null, continuation as Continuation<Any?>))
            }
        }
    }
}