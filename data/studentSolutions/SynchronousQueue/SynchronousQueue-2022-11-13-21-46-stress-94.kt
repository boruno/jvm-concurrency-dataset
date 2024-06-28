import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * An element is transferred from sender to receiver only when [send] and [receive]
 * invocations meet in time (rendezvous), so [send] suspends until another coroutine
 * invokes [receive] and [receive] suspends until another coroutine invokes [send].
 */
class SynchronousQueue<E> {
    private val senders: FAAQueue<Pair<Continuation<Unit>, E>> = FAAQueue()
    private val receivers: FAAQueue<Continuation<E>> = FAAQueue()

    /**
     * Sends the specified [element] to this channel, suspending if there is no waiting
     * [receive] invocation on this channel.
     */
    suspend fun send(element: E) {
        val continuation = receivers.dequeue()
        if (continuation != null) {
            continuation.resume(element)
        } else {
            suspendCoroutine { cont ->
                senders.enqueue(cont to element)
            }
        }
    }

    /**
     * Retrieves and removes an element from this channel if there is a waiting [send] invocation on it,
     * suspends the caller if this channel is empty.
     */
    suspend fun receive(): E {
        val p = senders.dequeue()
        return if (p != null) {
            val (s, elem) = p
            s.resume(Unit)
            elem
        } else {
            suspendCoroutine { cont ->
                receivers.enqueue(cont)
            }
        }
    }
}