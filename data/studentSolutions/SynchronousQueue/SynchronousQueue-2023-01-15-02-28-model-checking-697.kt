import kotlinx.atomicfu.atomic
import kotlin.coroutines.*

/**
 * An element is transferred from sender to receiver only when [send] and [receive]
 * invocations meet in time (rendezvous), so [send] suspends until another coroutine
 * invokes [receive] and [receive] suspends until another coroutine invokes [send].
 */
class SynchronousQueue<E> {
    private val head = atomic<Node>(DummyNode)
    private val tail = atomic<Node>(DummyNode)

    /**
     * Sends the specified [element] to this channel, suspending if there is no waiting
     * [receive] invocation on this channel.
     */
    suspend fun send(element: E) {
        while (true) {
            val curHead = head.value
            val next = curHead.next.value

            if (next is SenderNode?) {
                val curTail = tail.value

                val result = suspendCoroutine { cont ->
                    if (!curTail.next.compareAndSet(null, SenderNode(cont, element))) {
                        cont.resume(Retry)
                    }
                }

                val newTail = curTail.next.value ?: error("Next should not be null")
                tail.compareAndSet(curTail, newTail)

                if (result != Retry) {
                    assert(result == Unit)
                    return
                }
            } else {
                next as? ReceiverNode ?: error("Next should be receiverNode, but was $next")

                if (head.compareAndSet(curHead, next)) {
                    next.continuation.resume(element)
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
        while (true) {
            val curHead = head.value
            val next = curHead.next.value

            if (next is ReceiverNode?) {
                val curTail = tail.value

                val result = suspendCoroutine { cont ->
                    if (!curTail.next.compareAndSet(null, ReceiverNode(cont))) {
                        cont.resume(Retry)
                    }
                }

                val newTail = curTail.next.value ?: error("Next should not be null")
                tail.compareAndSet(curTail, newTail)

                if (result != Retry) {
                    @Suppress("UNCHECKED_CAST")
                    return result as E
                }
            } else {
                next as? SenderNode ?: error("Next should be receiverNode, but was $next")

                if (head.compareAndSet(curHead, next)) {
                    next.continuation.resume(Unit)
                }

                @Suppress("UNCHECKED_CAST")
                return next.element as E
            }
        }
    }

    private companion object {
        object Retry

        sealed class Node {
            val next = atomic<Node?>(null)
        }

        object DummyNode : Node()
        class SenderNode(val continuation: Continuation<Unit>, val element: Any?) : Node()
        class ReceiverNode(val continuation: Continuation<Any?>) : Node()
    }
}
