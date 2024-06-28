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

    private val head: AtomicRef<Node>
    private val tail: AtomicRef<Node>

    private val RECEIVING = Any()

    init {
        val dummy = Node(null, null)
        head = atomic(dummy)
        tail = atomic(dummy)
    }

    /**
     * Sends the specified [element] to this channel, suspending if there is no waiting
     * [receive] invocation on this channel.
     */
    suspend fun send(element: E) {
        while (true) {
            val h = head.value
            val t = tail.value

            if (h == t || t.x != RECEIVING) {
                if (null == suspendCoroutine { cont ->
                        val node = Node(element, cont)
                        if (t.next.compareAndSet(null, node)) {
                            tail.compareAndSet(t, node)
                        } else {
                            t.next.value?.let { tail.compareAndSet(t, it) }
                            cont.resume(RECEIVING)
                        }
                    }) {
                    break
                }
            } else {
                val next = h.next.value ?: continue
                if (head.compareAndSet(h, next)) {
                    next.cont!!.resume(element)
                    break
                }
            }
        }
    }

    /**
     * Retrieves and removes an element from this channel if there is a waiting [send] invocation on it,
     * suspends the caller if this channel is empty.
     */
    suspend fun receive(): E {
        while (true) {
            val h = head.value
            val t = tail.value

            if (h == t || t.x == RECEIVING) {
                val e = suspendCoroutine { cont ->
                    val node = Node(RECEIVING, cont)
                    if (t.next.compareAndSet(null, node)) {
                        tail.compareAndSet(t, node)
                    } else {
                        t.next.value?.let { tail.compareAndSet(t, it) }
                        cont.resume(null)
                    }
                }

                if (e != null)
                    return e as E
            } else {
                val next = h.next.value ?: continue
                if (head.compareAndSet(h, next)) {
                    next.cont!!.resume(null)
                    return next.x as E
                }
            }
        }

    }
}

class Node(val x: Any?, val cont: Continuation<Any?>?) {

    val next: AtomicRef<Node?> = atomic(null)
}