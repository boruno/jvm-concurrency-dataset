

import kotlinx.atomicfu.*
import kotlin.coroutines.Continuation
import kotlin.coroutines.*
/**
 * An element is transferred from sender to receiver only when [send] and [receive]
 * invocations meet in time (rendezvous), so [send] suspends until another coroutine
 * invokes [receive] and [receive] suspends until another coroutine invokes [send].
 */
class SynchronousQueue<E> {
    private val emptyNode: Node<E> = Node(null, Type.Send)
    private val head: AtomicRef<Node<E>> = atomic(emptyNode)
    private val tail: AtomicRef<Node<E>> = atomic(emptyNode)
    /**
     * Sends the specified [element] to this channel, suspending if there is no waiting
     * [receive] invocation on this channel.
     */
    suspend fun send(element: E): Unit {
        val offer = Node(element, Type.Send)
        while (true) {
            var h = head.value
            val t = head.value
            if (h != t && t.type != Type.Send) {
                val hNext = h.next.value
                if (hNext == null || h != head.value || t != tail.value) {
                    continue
                }
                val ok = hNext.element.compareAndSet(null, element)
                head.compareAndSet(h, hNext)
                if (ok) {
                    hNext.receiveCompletion!!.resume(false)
                    return
                }
            } else {
                val tNext = t.next.value
                if (t == tail.value) {
                    if (tNext == null) {
                        if (!calc(offer, t, element)) {
                            h = head.value
                            if (offer == h.next.value) {
                                head.compareAndSet(h, offer)
                            }
                            return
                        }
                    } else {
                        tail.compareAndSet(t, tNext)
                    }
                }
            }
        }
    }

    /**
     * Retrieves and removes an element from this channel if there is a waiting [send] invocation on it,
     * suspends the caller if this channel is empty.
     */
    suspend fun receive(): E {
        val offer: Node<E> = Node(null, Type.Receive)
        while (true) {
            val t = tail.value
            var h = head.value
            if (h != t && t.type != Type.Receive) {
                val hNext = h.next.value
                if (hNext == null || h != head.value || t != tail.value) {
                    continue
                }
                val sendCompletion = hNext.sendCompletion
                if (sendCompletion == null) {
                    continue
                }
                val (continuation, element) = sendCompletion
                val ok = hNext.element.compareAndSet(element, null)
                head.compareAndSet(h, hNext)
                if (ok) {
                    continuation.resume(false)
                    return element
                }
            } else {
                val tNext = t.next.value
                if (t == tail.value) {
                    if (tNext == null) {
                        if (!calc(offer, t, null)) {
                            h = head.value
                            if (offer == h.next.value) {
                                head.compareAndSet(h, offer)
                            }
                            return offer.element.value!!
                        }
                    } else {
                        tail.compareAndSet(t, tNext)
                    }
                }
            }
        }
    }

    private suspend fun calc(offer: Node<E>, n: Node<E>, elem: E?): Boolean {
        return suspendCoroutine { completion ->
            if (elem == null) {
                offer.receiveCompletion = completion
            } else {
                offer.sendCompletion = Pair(completion, elem)
            }
            if (n.next.compareAndSet(null, offer)) {
                tail.compareAndSet(n, offer)
            } else {
                completion.resume(true)
            }
        }
    }
}

private enum class Type {
    Send,
    Receive
}

private class Node<E> {
    val type: Type
    val element: AtomicRef<E?>
    val next: AtomicRef<Node<E>?>

    var receiveCompletion: Continuation<Boolean>?
    var sendCompletion: Pair<Continuation<Boolean>, E>?

    constructor(element: E?, type: Type) {
        this.type = type
        this.element = atomic(element)
        this.next = atomic(null)
        this.receiveCompletion = null
        this.sendCompletion = null
    }
}