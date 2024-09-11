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

    init {
        val dummy = Node(false)
        dummy.consumed.compareAndSet(expect = false, update = true)
        head = atomic(dummy)
        tail = atomic(dummy)
    }

    /**
     * Sends the specified [element] to this channel, suspending if there is no waiting
     * [receive] invocation on this channel.
     */
    suspend fun send(element: E) {
        val node = Node(true)
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
    @Suppress("UNCHECKED_CAST")
    suspend fun receive(): E {
        var node = Node(false)

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
                    if (!node.consumed.compareAndSet(expect = false, update = true)) {
                        node = Node(false)
                        continue
                    } // v1
                    // after successful resume
                    val headAfterContinuation = head.value
                    if (node == headAfterContinuation.next.value) {
                        head.compareAndSet(headAfterContinuation, node)
                    }
                    return node.element.value!! as E
                }
            } else {
                if (curHead != head.value) continue //-v1, v2
                val nextHead = curHead.next.value ?: continue
                val result = nextHead.element.value
                if (result != null && !nextHead.consumed.value) {
                    if (nextHead.consumed.compareAndSet(expect = false, update = true)) {
                        head.compareAndSet(curHead, nextHead)
                        nextHead.continuation.resume(true)
                        return result as E
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

private class Node(val isSend: Boolean) {
    val element = atomic<Any?>(null)
    val next = atomic<Node?>(null)
    lateinit var continuation: Continuation<Boolean>
    val consumed = atomic<Boolean>(false)
}