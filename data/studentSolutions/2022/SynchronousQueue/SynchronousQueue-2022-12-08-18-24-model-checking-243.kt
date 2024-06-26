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
        val offer = Node<E>(false)
        offer.data.value = element
        while (true) {
            val t = tail.value
            var h = head.value
            if (h == t || !t.isReceive) {
                val n = t.next.value
                if (t == tail.value) {
                    if (n != null) {
                        tail.compareAndSet(t, n)
                    } else {
                        suspendCoroutine<E?> sc@{ cont ->
                            offer.cont = cont
                            if (!t.next.compareAndSet(n, offer)) {
                                cont.resume(null)
                                return@sc
                            }
                            tail.compareAndSet(t, offer)
                        } ?: continue
                        h = head.value
                        if (offer == h.next.value) {
                            head.compareAndSet(h, offer)
                        }
                        return
                    }
                }
            } else {
                val n = h.next.value
                if (t != tail.value || h != head.value || n == null) {
                    continue
                }
                val success = n.data.compareAndSet(null, element)
                head.compareAndSet(h, n)
                if (success) {
                    n.cont!!.resume(element)
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
        val offer = Node<E>(true)
        while (true) {
            val t = tail.value
            var h = head.value
            if (h == t || t.isReceive) {
                val n = t.next.value
                if (t == tail.value) {
                    if (n != null) {
                        tail.compareAndSet(t, n)
                    } else {
                        val result = suspendCoroutine<E?> sc@{ cont ->
                            offer.cont = cont
                            if (!t.next.compareAndSet(n, offer)) {
                                cont.resume(null)
                                return@sc
                            }
                            tail.compareAndSet(t, offer)
                        } ?: continue
                        h = head.value
                        if (offer == h.next.value) {
                            head.compareAndSet(h, offer)
                        }
                        return result
                    }
                }
            } else {
                val n = h.next.value
                if (t != tail.value || h != head.value || n == null) {
                    continue
                }
                val data = n.data.value ?: continue
                val success = n.data.compareAndSet(data, null)
                head.compareAndSet(h, n)
                if (success) {
                    n.cont!!.resume(data)
                    return data
                }
            }
        }
    }
}

private class Node<E>(val isReceive: Boolean) {
    val next = atomic<Node<E>?>(null)
    val data = atomic<E?>(null)
    var cont: Continuation<E>? = null
}