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
        val dummy = Node<E>(false)
        head = atomic(dummy)
        tail = atomic(dummy)
    }

    /**
     * Sends the specified [element] to this channel, suspending if there is no waiting
     * [receive] invocation on this channel.
     */
    suspend fun send(element: E) {
        val node = Node<E>(true)
        node.element.compareAndSet(null, element)

        while (true) {
            val curHead = head.value
            val curTail = tail.value
            if (curTail == curHead || curTail.isSend) { // if empty or all elements in queue are senders
                val nextTail = curTail.next.value
                if (nextTail != null) {
                    tail.compareAndSet(curTail, nextTail)
                    continue
                }

                if (suspendCoroutine { cont ->
                        node.continuation = cont
                        if (curTail.next.compareAndSet(null, node)) {
                            tail.compareAndSet(curTail, node)
                        } else {
                            cont.resume(false)
                        }
                    }) {
                    // after successful resume
                    val headAfterContinuation = head.value
                    if (node == headAfterContinuation.next.value) {
                        head.compareAndSet(headAfterContinuation, node)
                    }
                    return
                }
            } else {
                val nextHead = curHead.next.value ?: continue
                if (nextHead.element.compareAndSet(null, element)) {
                    head.compareAndSet(curHead, nextHead)
                    nextHead.continuation.resume(true)
                    return
                } else {
                    head.compareAndSet(curHead, nextHead)
                    continue
                }
            }
        }
    }

    /**
     * Retrieves and removes an element from this channel if there is a waiting [send] invocation on it,
     * suspends the caller if this channel is empty.
     */
    suspend fun receive(): E {
        val node = Node<E>(false)

        while (true) {
            val curHead = head.value
            val curTail = tail.value
            if (curTail == curHead || !curTail.isSend) { // if empty or all elements in queue are senders
                val nextTail = curTail.next.value
                if (nextTail != null) {
                    tail.compareAndSet(curTail, nextTail)
                    continue
                }

                if (suspendCoroutine { cont ->
                        node.continuation = cont
                        if (curTail.next.compareAndSet(null, node)) {
                            tail.compareAndSet(curTail, node)
                        } else {
                            cont.resume(false)
                        }
                    }) {
                    // after successful resume
                    val headAfterContinuation = head.value
                    if (node == headAfterContinuation.next.value) {
                        head.compareAndSet(headAfterContinuation, node)
                    }
                    return node.element.value!!
                }
            } else {
                val nextHead = curHead.next.value ?: continue
                val result = nextHead.element.value
                if (result != null) {
                    if (nextHead.element.compareAndSet(result, null)) {
                        head.compareAndSet(curHead, nextHead)
                        nextHead.continuation.resume(true)
                        return result
                    } else {
                        head.compareAndSet(curHead, nextHead)
                    }
                } else {
                    head.compareAndSet(curHead, nextHead)
                    continue
                }
            }
        }
    }
}

private class Node<E>(val isSend: Boolean) {
    val element = atomic<E?>(null)
    val next = atomic<Node<E>?>(null)
    lateinit var continuation: Continuation<Boolean>
}