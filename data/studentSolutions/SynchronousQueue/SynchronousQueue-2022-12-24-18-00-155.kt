import kotlin.coroutines.suspendCoroutine
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume

/**
 * An element is transferred from sender to receiver only when [send] and [receive]
 * invocations meet in time (rendezvous), so [send] suspends until another coroutine
 * invokes [receive] and [receive] suspends until another coroutine invokes [send].
 */
class SynchronousQueue<E> {
    private val head: AtomicReference<Node> = AtomicReference(Node())
    private val tail: AtomicReference<Node> = AtomicReference(Node())

    /**
     * Sends the specified [element] to this channel, suspending if there is no waiting
     * [receive] invocation on this channel.
     */
    suspend fun send(element: E) {
        val offer = Node(Type.SEND, element)
        while (true) {
            val t = tail.get()
            var h = head.get()
            if (h == t || t.isSend()) {
                val n = t.next.get()
                if (t == tail.get()) {
                    if (null != n) {
                        tail.compareAndSet(t, n)
                    } else {
                        if (calcCoroutine(offer, t, element)) continue
                        h = head.get()
                        if (offer == h.next.get()) {
                            head.compareAndSet(h, offer)
                        }
                        return
                    }
                }
            } else {
                val n = h.next.get()
                if (t != tail.get() || h != head.get() || n == null) {
                    continue  // inconsistent snapshot
                }
                val success = n.element.compareAndSet(null, element)
                head.compareAndSet(h, n)
                if (success) {
                    n.continuationForReceive!!.resume(false)
                    return
                }
            }
        }
    }

    /**
     * Retrieves and removes an element from this channel if there is a waiting [send] invocation on it,
     * suspends the caller if this channel is empty.
     */
    suspend fun receive(): E {
        val offer = Node(Type.RECEIVE, null)
        while (true) {
            val t = tail.get()
            var h = head.get()
            if (h == t || t.isReceive()) {
                val n = t.next.get()
                if (t == tail.get()) {
                    if (null != n) {
                        tail.compareAndSet(t, n)
                    } else {
                        if (calcCoroutine(offer, t, null)) continue
                        h = head.get()
                        if (offer == h.next.get()) {
                            head.compareAndSet(h, offer)
                        }
                        return offer.element.get()!!
                    }
                }
            } else {
                val n = h.next.get()
                if (t != tail.get() || h != head.get() || n == null) {
                    continue // inconsistent snapshot
                }
                val (continuation, element) = n.continuationForSend!!
                val success = n.element.compareAndSet(element, null)
                head.compareAndSet(h, n)
                if (success) {
                    continuation.resume(false)
                    return element
                }
            }
        }
    }

    private suspend fun calcCoroutine(offer: Node, t: Node, element: E?): Boolean =
        suspendCoroutine { continuation ->
            if (element == null) {
                offer.continuationForReceive = continuation
            } else {
                offer.continuationForSend = (continuation to element)
            }
            if (t.next.compareAndSet(null, offer)) {
                tail.compareAndSet(t, offer)
            } else {
                continuation.resume(true)
            }
        }

    private inner class Node(val type: Type? = null, element: E? = null) {
        val element = AtomicReference(element)
        val next = AtomicReference<Node?>(null)

        var continuationForSend: Pair<Continuation<Boolean>, E>? = null
        var continuationForReceive: Continuation<Boolean>? = null

        fun isSend() = type == Type.SEND
        fun isReceive() = type == Type.RECEIVE
    }

    private enum class Type {
        SEND,
        RECEIVE
    }
}