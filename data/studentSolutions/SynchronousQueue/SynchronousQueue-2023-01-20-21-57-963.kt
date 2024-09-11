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
        val dummy = Node<E>(NodeType.SENDER, null)
        head = atomic(dummy)
        tail = atomic(dummy)
    }

    /**
     * Sends the specified [element] to this channel, suspending if there is no waiting
     * [receive] invocation on this channel.
     */
    suspend fun send(element: E) {
        val offer = Node(NodeType.SENDER, element)
        while (true) {
            val curTail = tail.value
            var curHead = head.value
            if (curHead == curTail || curTail.type == NodeType.SENDER) {
                val next = curTail.next.value
                if (curTail != tail.value) {
                    continue
                }
                if (next != null) {
                    tail.compareAndSet(curTail, next)
                    continue
                }
                if (suspendCoroutine { cont ->
                        offer.continuationSend = (cont to element)
                        if (curTail.next.compareAndSet(null, offer)) {
                            tail.compareAndSet(curTail, offer)
                        } else {
                            cont.resume(true)
                        } }) continue
                curHead = head.value
                if (offer == curHead.next.value) {
                    head.compareAndSet(curHead, offer)
                }
                return
            } else {
                val next = curTail.next.value
                if (curTail != tail.value || curHead != head.value || next == null) continue
                val success = next.element.compareAndSet(null, element)
                head.compareAndSet(curHead, next)
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
        val offer = Node<E>(NodeType.RECEIVER, null)
        while (true) {
            val curTail = tail.value
            var curHead = head.value
            if (curHead == curTail || curTail.type == NodeType.RECEIVER) {
                val next = curTail.next.value
                if (curTail != tail.value) {
                    continue
                }
                if (next != null) {
                    tail.compareAndSet(curTail, next)
                    continue
                }
                if (suspendCoroutine { cont ->
                        offer.continuationReceive = cont
                        if (curTail.next.compareAndSet(null, offer)) {
                            tail.compareAndSet(curTail, offer)
                        } else {
                            cont.resume(true)
                        } }) continue
                curHead = head.value
                if (offer == curHead.next.value) {
                    head.compareAndSet(curHead, offer)
                }
                return offer.element.value!!
            } else {
                val next = curTail.next.value
                if (curTail != tail.value || curHead != head.value || next == null) continue
                val (cont, element) = next.continuationSend!!
                val success = next.element.compareAndSet(element, null)
                head.compareAndSet(curHead, next)
                if (success) {
                    cont.resume(false)
                    return element
                }
            }
        }
    }
}


private class Node<E>(val type : NodeType, value : E?) {
    val next = atomic<Node<E>?>(null)
    val element = atomic(value)

    var continuationSend : Pair<Continuation<Boolean>, E>? = null
    var continuationReceive : Continuation<Boolean>? = null
}

private enum class NodeType {
    SENDER, RECEIVER, DUMMY
}