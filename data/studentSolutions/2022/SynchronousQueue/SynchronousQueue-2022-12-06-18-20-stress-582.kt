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
        val dummy = Node<E>(false)
        head = atomic(dummy)
        tail = atomic(dummy)
    }

    /**
     * Sends the specified [element] to this channel, suspending if there is no waiting
     * [receive] invocation on this channel.
     */
    suspend fun send(element: E) {
        while (true) {
            val t = tail.value
            val h = head.value
            if (h == t || !t.isReceive) {
                val n = t.next.value
                if (t == tail.value) {
                    if (n != null) {
                        tail.compareAndSet(t, n)
                    } else {
                        suspendCoroutine<Unit> { cont ->
                            val offer = Node<E>(false)
                            offer.sender = cont to element
                            if (t.next.compareAndSet(n, offer)) {
                                tail.compareAndSet(t, offer)
                            }
                        }
                    }
                }
            } else {
                val n = h.next.value
                if (t != tail.value || h != head.value || n == null) {
                    continue
                }
                val s = n.receiver
                head.compareAndSet(h, n)
                s!!.resume(element)
            }
        }
    }

    /**
     * Retrieves and removes an element from this channel if there is a waiting [send] invocation on it,
     * suspends the caller if this channel is empty.
     */
    suspend fun receive(): E {
        while (true) {
            val t = tail.value
            val h = head.value
            if (h == t || t.isReceive) {
                val n = t.next.value
                if (t == tail.value) {
                    if (n != null) {
                        tail.compareAndSet(t, n)
                    } else {
                        return suspendCoroutine<E> { cont ->
                            val offer = Node<E>(true)
                            offer.receiver = cont
                            if (t.next.compareAndSet(n, offer)) {
                                tail.compareAndSet(t, offer)
                            }
                        }
                    }
                }
            } else {
                val n = h.next.value
                if (t != tail.value || h != head.value || n == null) {
                    continue
                }
                val (s, elem) = n.sender!!
                head.compareAndSet(h, n)
                s.resume(Unit)
                return elem
            }
        }
    }
}

private class Node<E>(val isReceive: Boolean) {
    val next = atomic<Node<E>?>(null)
    var sender: Pair<Continuation<Unit>, E>? = null
    var receiver: Continuation<E>? = null
}