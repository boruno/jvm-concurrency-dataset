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
    private val head: AtomicRef<Node<Pair<Continuation<E?>, E?>>>
    private val tail: AtomicRef<Node<Pair<Continuation<E?>, E?>>>

    init {
        val dummy = Node<Pair<Continuation<E?>, E?>>(null)
        head = atomic(dummy)
        tail = atomic(dummy)
    }

    /**
     * Sends the specified [element] to this channel, suspending if there is no waiting
     * [receive] invocation on this channel.
     */
    suspend fun send(element: E): Unit {
        while (true) {
            val t = tail.value
            val h = head.value
            if (h.next.value == null || t.x?.second != null) {
                val res = suspendCoroutine sc@ { cont ->
                    val newNode = Node(cont to element as E?)
                    if (t.next.compareAndSet(null, newNode)) {
                        tail.compareAndSet(t, newNode)
                    } else {
                        t.next.value?.let { tail.compareAndSet(t, it) }
                        cont.resume(null)
                        return@sc
                    }
                }
                if (res != null) {
                    break
                }
            } else {
                val next = h.next.value
                if (next != null && head.compareAndSet(h, next)) {
                    next.x?.first?.resume(element)
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
            val t = tail.value
            val h = head.value
            if (t == h || t.x?.second == null) {
                val res = suspendCoroutine sc@ { cont ->
                    val newNode = Node(cont to null as E?)
                    if (t.next.compareAndSet(null, newNode)) {
                        tail.compareAndSet(t, newNode)
                    } else {
                        t.next.value?.let { tail.compareAndSet(t, it) }
                        cont.resume(null)
                        return@sc
                    }
                }
                if (res != null) {
                    return res
                }
            } else {
                val next = h.next.value
                if (next != null && head.compareAndSet(h, next)) {
                    val res = next.x?.second ?: throw Error("receiver has gotten null from sender")
                    next.x.first.resume(null)
                    return res
                }
            }
        }
    }
}

private class Node<E>(val x: E?) {
    val next = atomic<Node<E>?>(null)
}
