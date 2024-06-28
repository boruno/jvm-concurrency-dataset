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
        val newNode: Node<E> = Node(null, null)
        head = atomic(newNode)
        tail = atomic(newNode)
    }
    /**
     * Sends the specified [element] to this channel, suspending if there is no waiting
     * [receive] invocation on this channel.
     */
    suspend fun send(element: E): Unit {
        val node = Node(NodeType.SENDER, element)
        var curHead: Node<E>
        var curTail: Node<E>

        while (true) {
            curHead = head.value
            curTail = tail.value
            if (curTail.isSender() || curHead == curTail) {
                if (!check(curTail, node)) continue
                return
            }
            val next = curHead.next.value
            if (next == null || curTail != tail.value || curHead != head.value || curHead == tail.value)
                continue
            if (next.isReceiver() && next.continuation !== null && head.compareAndSet(curHead, next)) {
                next.value.compareAndSet(null, element)
                next.continuation!!.resume(true)
                return
            }
        }
    }

    /**
     * Retrieves and removes an element from this channel if there is a waiting [send] invocation on it,
     * suspends the caller if this channel is empty.
     */
    suspend fun receive(): E {
        val node: Node<E> = Node(NodeType.RECEIVER, null)
        var curHead: Node<E>
        var curTail: Node<E>
        while (true) {
            curHead = head.value
            curTail = tail.value
            if (curTail.isReceiver() || curHead == curTail) {
                if (!check(curTail, node)) continue
                return node.value.value!!
            }
            val next = curHead.next.value
            if (next == null || curHead == tail.value || curTail != tail.value || curHead != head.value)
                continue
            val element = next.value.value ?: continue
            if (next.continuation !== null && head.compareAndSet(curHead, next)) {
                next.value.compareAndSet(element, null)
                next.continuation!!.resume(true)
                return element
            }
        }
    }

    private suspend fun check(curTail: Node<E>, node: Node<E>): Boolean {
        return suspendCoroutine sc@{ continuation ->
            node.continuation = continuation
            if (curTail.next.compareAndSet(null, node)) {
                tail.compareAndSet(curTail, node)
            } else {
                continuation.resume(false)
                return@sc
            }
        }
    }

    class Node<E>(private val type: NodeType?, v: E?) {
        val value: AtomicRef<E?> = atomic(v)
        val next: AtomicRef<Node<E>?> = atomic(null)
        var continuation: Continuation<Boolean>? = null

        fun isSender(): Boolean { return type == NodeType.SENDER}
        fun isReceiver(): Boolean { return type == NodeType.RECEIVER}
    }

    enum class NodeType {
        SENDER,
        RECEIVER
    }
}