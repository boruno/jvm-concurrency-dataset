import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

private enum class NodeType { Send, Receive }
private open class Node<E>(e: E?, val type: NodeType) {
    val element = atomic(e)
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

    private suspend fun coroutineTest(t: Node<E>, node: Node<E>) = suspendCoroutine sc@{ continuation ->
        node.continuation = continuation
        if (t.next.compareAndSet(null, node)) { tail.compareAndSet(t, node); return@sc }
        t.next.value?.let { tail.compareAndSet(t, it) }
        continuation.resume(false)
    }

    /**
     * Sends the specified [element] to this channel, suspending if there is no waiting
     * [receive] invocation on this channel.
     */
    suspend fun send(element: E) {
        while (true) {
            val h = head.value
            val t = tail.value
            val toSend: Node<E> = Node(element, NodeType.Send)

            if ((t.type == NodeType.Send || h == t) && coroutineTest(t, toSend)) break

            val next = h.next.value ?: continue
            if (!(next.type == NodeType.Receive && next.continuation != null && head.compareAndSet(h, next))) continue

            next.element.compareAndSet(null, element)
            next.continuation?.resume(true)
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

            if ((h == t || t.type == NodeType.Receive) && coroutineTest(t, toReceive)) return toReceive.element.value!!

            val next = h.next.value ?: continue
            val element = next.element.value ?: continue
            if (next.type == NodeType.Send && next.continuation != null && head.compareAndSet(h, next)) continue

            next.element.compareAndSet(element, null)
            next.continuation?.resume(true)
            return element
        }
    }
}