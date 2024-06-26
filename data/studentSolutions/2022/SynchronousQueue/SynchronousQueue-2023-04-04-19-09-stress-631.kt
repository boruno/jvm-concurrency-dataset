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
    val tail: AtomicRef<Node<E>>
    val head: AtomicRef<Node<E>>

    init {
        val dummy = Node<E>(null, Type.Item, null, null)
        tail = atomic(dummy)
        head = atomic(dummy)
    }

    suspend fun suspendNode(offer: Node<E>, t: Node<E>): Boolean {
        // roughly corresponds to lines 24 - 27 in the Synchronous Queue from The art of Multiprocessor programming
        val ans = suspendCoroutine { continuation ->
            offer.continuation = continuation

            val n = t.next.value
            if (t.next.compareAndSet(null, offer)) {
                tail.compareAndSet(t, offer)
            } else {
                if (n != null) {
                    tail.compareAndSet(t, n)
                }
                continuation.resume(false)
            }
        }
        return ans
    }

    /**
     * Sends the specified [element] to this channel, suspending if there is no waiting
     * [receive] invocation on this channel.
     */
    suspend fun send(element: E) {
        val offer = Node(element, Type.Item, null, null)

        while (true) {
            val t = tail.value
            val h = head.value

            if (t == h || t.type == Type.Item) {
                if (suspendNode(offer, t)) {
                    break
                }
            } else {
                val n = h.next.value
                if (t != tail.value || h != head.value || n == null) {
                    continue // empty or something changed
                }
                // list is nonempty and the value is not a reservation -- rendezvous

//                if (head.compareAndSet(h, n)) {
                head.compareAndSet(h, n)
                n.data.value = element
                n.continuation!!.resume(true)
                return
//                }
            }
        }
    }

    /**
     * Retrieves and removes an element from this channel if there is a waiting [send] invocation on it,
     * suspends the caller if this channel is empty.
     */
    suspend fun receive(): E {
        val offer: Node<E> = Node(null, Type.Reservation, null, null)

        while (true) {
            val t = tail.value
            val h = head.value

            if (t == h || t.type == Type.Reservation) {
                if (t == tail.value) {
                    if (suspendNode(offer, t)) {
                        return offer.data.value!!
                    }
                }
            } else {
                val n = h.next.value
                if (t != tail.value || h != head.value || n == null) {
                    continue // empty or something changed
                }
                // list is nonempty and the value is not a reservation -- rendezvous
//                if (head.compareAndSet(h, n)) {
                head.compareAndSet(h, n)
                n.continuation!!.resume(true)
                return n.data.value!!
//                }
            }
        }
    }
}

class Node<E>(data: E?, val type: Type, next: Node<E>?, var continuation: Continuation<Boolean>?) {
    val next = atomic(next)
    val data = atomic(data)
}

enum class Type {
    Item,
    Reservation,
}