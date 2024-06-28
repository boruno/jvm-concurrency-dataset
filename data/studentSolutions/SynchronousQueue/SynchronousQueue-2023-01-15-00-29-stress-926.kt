import kotlinx.atomicfu.AtomicBoolean
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
        val dummy = Node<E>(null, true)
        head = atomic(dummy)
        tail = atomic(dummy)
    }
    /**
     * Sends the specified [element] to this channel, suspending if there is no waiting
     * [receive] invocation on this channel.
     */
    suspend fun send(element: E) {
        val offer = Node(element, true)
        while (true) {
            val t = tail.value
            var h = head.value
            if (h == t || t.isSend) {
                val n = t.next.value
                if (t == tail.value) {
                    if (n != null) {
                        tail.compareAndSet(t, n)
                    } else {
                        // TODO: what?
                        if (runCoroutine(t, offer, element)) continue
                        h = head.value
                        if (offer == h.next.value) {
                            head.compareAndSet(h, offer)
                        }
                        return
                    }
                }
            } else {
                val n = h.next.value
                if (t != tail.value || h != head.value || n == null) continue
                val success = n.data.compareAndSet(null, element)
                head.compareAndSet(h, n)
                if (success) {
                    // TODO: what??
                    n.receiveCont!!.resume(false)
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
        val offer:Node<E> = Node(null, false)
        while (true) {
            val t = tail.value
            var h = head.value
            if (h == t || !t.isSend) {
                val n = t.next.value
                if (t == tail.value) {
                    if (n != null) {
                        tail.compareAndSet(t, n)
                    } else {
                        // TODO: what?
                        if (runCoroutine(offer, t, null)) continue
                        h = head.value
                        if (offer == h.next.value) {
                            head.compareAndSet(h, offer)
                        }
                        return offer.data.value!!
                    }
                }
            } else {
                val n = h.next.value
                if (t != tail.value || h != head.value || n == null) continue
                val (cont, element) = n.sendCont!!
                val success = n.data.compareAndSet(element, null)
                head.compareAndSet(h, n)
                if (success) {
                    // TODO: what??
                    cont.resume(false)
                    return element
                }
            }
        }
    }

    suspend fun runCoroutine(t: Node<E>, offer: Node<E>, element: E?): Boolean =
        suspendCoroutine { cont ->
            if (element == null) {
                offer.receiveCont = cont
            } else {
                offer.sendCont = cont to element
            }
            if (t.next.compareAndSet(null, offer)) {
                tail.compareAndSet(t, offer)
            } else {
                cont.resume(true)
            }
        }
}

class Node<E>(data: E?,
              isSend: Boolean,
              next: Node<E>? = null) {
    val data: AtomicRef<E?>
    val next: AtomicRef<Node<E>?>
    var sendCont: Pair<Continuation<Boolean>, E>? = null
    var receiveCont: Continuation<Boolean>? = null
    val _isSend: AtomicBoolean
    init {
        this.data = atomic(data)
        this.next = atomic(next)
        this._isSend = atomic(isSend)
    }

    val isSend: Boolean get() = _isSend.value
}