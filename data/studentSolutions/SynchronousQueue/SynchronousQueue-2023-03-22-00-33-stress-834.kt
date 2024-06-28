import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic

/**
 * An element is transferred from sender to receiver only when [send] and [receive]
 * invocations meet in time (rendezvous), so [send] suspends until another coroutine
 * invokes [receive] and [receive] suspends until another coroutine invokes [send].
 */
class SynchronousQueue<E> {
    val tail: AtomicRef<Node<E>>
    val head: AtomicRef<Node<E>>

    init {
        val dummy = Node<E>(null, Type.Item, null)
        tail = atomic(dummy)
        head = atomic(dummy)
    }

    /**
     * Sends the specified [element] to this channel, suspending if there is no waiting
     * [receive] invocation on this channel.
     */
    suspend fun send(element: E): Unit {
        val offer = Node(element, Type.Item, null)

        while (true) {
            val t = tail.value
            var h = head.value
            if (t == h || t.type == Type.Item) {
                val n = t.next.value
                if (t == tail.value) {
                    if (n != null) {
                        tail.compareAndSet(t, n)
                    } else if (t.next.compareAndSet(n, offer)) {
                        tail.compareAndSet(t, offer)
                        while (offer.data.value == element) {
                            // spins
                        }
                        // the rendez-vouz - someone dequeued or changed

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
        val offer : Node<E> = Node(null, Type.Reservation, null)

        while (true) {
            val t = tail.value
            var h = head.value
            if (t == h || t.type == Type.Reservation) {
                val n = t.next.value
                if (t == tail.value) {
                    if (n != null) {
                        tail.compareAndSet(t, n)
                    } else if (t.next.compareAndSet(n, offer)) {
                        tail.compareAndSet(t, offer)

                        while (offer.data.value == null) {
                            // spins
                        }

                        // this cleaning up is unnecessary
                        h = head.value
                        if (offer == h.next.value) {
                            head.compareAndSet(h, offer)
                        }
                        return offer.data.value!!
                    }
                }
            } else {
                val n = h.next.value
                if (t != tail.value || h != head.value || n == null) {
                    continue
                }
                val success = n.data.compareAndSet(null, null)
                head.compareAndSet(h, n)
                if (!success) {
                    return n.data.value!!
                }
            }
        }
    }
}

class Node<E>(data: E?, val type: Type, next: Node<E>?) {
    val next = atomic(next)
    val data = atomic(data)
}

enum class Type {
    Item,
    Reservation,
}