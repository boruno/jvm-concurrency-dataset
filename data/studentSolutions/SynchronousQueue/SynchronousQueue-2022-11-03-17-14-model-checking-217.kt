import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * An element is transferred from sender to receiver only when [send] and [receive]
 * invocations meet in time (rendezvous), so [send] suspends until another coroutine
 * invokes [receive] and [receive] suspends until another coroutine invokes [send].
 */

/**
 * @author :Цветков Николай
 */

class SynchronousQueue<E> {
    private val senders = FAAQueue<Pair<Continuation<Boolean>, E>>() // pair = continuation + element
    private val receivers = FAAQueue<Continuation<E>>()
    /**
     * Sends the specified [element] to this channel, suspending if there is no waiting
     * [receive] invocation on this channel.
     */
    suspend fun send(element: E): Unit =
        if (!receivers.isEmpty) {
        val r = receivers.dequeue()
        r!!.resume(element)
    } else {
        val res = suspendCoroutine<Boolean>sc@  { cont ->
            senders.enqueue(cont to element)
            if (!senders.isEmpty) {
                cont.resume(true)
                return@sc
            }
        }
            if (!res) {
                send(element)
            } else {

            }
        }

    /**
     * Retrieves and removes an element from this channel if there is a waiting [send] invocation on it,
     * suspends the caller if this channel is empty.
     */
    suspend fun receive(): E {
        if (!senders.isEmpty) {
            val (s, elem) = senders.dequeue()!!
            s.resume(true)
            return elem
        } else {
            return suspendCoroutine { cont ->
                receivers.enqueue(cont)
            }
        }
    }
}