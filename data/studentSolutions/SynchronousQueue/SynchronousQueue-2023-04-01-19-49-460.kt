import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

private enum class NodeType { Send, Receive }
private open class Node<E>(v: E?, val type: NodeType) {
    val value = atomic(v)
    val next: AtomicRef<Node<E>?> = atomic(null)
    var continuation: Continuation<Boolean>? = null
}

/**
 * An element is transferred from sender to receiver only when [send] and [receive]
 * invocations meet in time (rendezvous), so [send] suspends until another coroutine
 * invokes [receive] and [receive] suspends until another coroutine invokes [send].
 */
class SynchronousQueue<E> {
    private val head: AtomicRef<Node<E>>
    private val tail: AtomicRef<Node<E>>

    init {
        val dummy: Node<E> = Node(null, NodeType.Send)
        head = atomic(dummy)
        tail = atomic(dummy)
    }

    /**
     * Sends the specified [element] to this channel, suspending if there is no waiting
     * [receive] invocation on this channel.
     */
    suspend fun send(element: E): Unit {
        while (true) {
            val h = head.value
            val t = tail.value
            val toSend: Node<E> = Node(element, NodeType.Send)

            val ch = suspendCoroutine sc@{ continuation ->
                toSend.continuation = continuation
                if (t.next.compareAndSet(null, toSend)) { tail.compareAndSet(t, toSend); return@sc }
                tail.compareAndSet(t, t.next.value!!)
                continuation.resume(false)
            }

            if ((t.type == NodeType.Send || h == t) && ch) break

            val next = h.next.value ?: continue

            if (!(next.type == NodeType.Receive && next.continuation != null && head.compareAndSet(h, next))) continue

            next.value.compareAndSet(null, element)
            next.continuation!!.resume(true)
            return
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
            val toReceive: Node<E> = Node(null, NodeType.Receive)

            val ch = suspendCoroutine sc@{ continuation ->
                toReceive.continuation = continuation
                if (t.next.compareAndSet(null, toReceive)) { tail.compareAndSet(t, toReceive); return@sc }
                tail.compareAndSet(t, t.next.value!!)
                continuation.resume(false)
            }

            if ((t.type == NodeType.Receive || h == t) && ch) return toReceive.value.value!!
            else {
                val next = h.next.value ?: continue
                val element = next.value.value ?: continue

                if (next.type == NodeType.Send && next.continuation != null && this.head.compareAndSet(h, next)) {
                    next.value.compareAndSet(element, null)
                    next.continuation!!.resume(true)
                    return element
                }
            }
        }
    }
}