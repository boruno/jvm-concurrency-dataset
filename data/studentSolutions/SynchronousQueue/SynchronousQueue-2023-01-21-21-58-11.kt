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
        val dummy = Node<E>(null, false, null, null)
        head = atomic(dummy)
        tail = atomic(dummy)
    }

    /**
     * Sends the specified [element] to this channel, suspending if there is no waiting
     * [receive] invocation on this channel.
     */
    suspend fun send(element: E): Unit {
        while (true) {
            val curTail = tail.value
            val curHead = head.value

            if (curTail == curHead || curTail.isSender) {
                suspendCoroutine { cont ->
                    val nodeToEnqueue = Node(element, true, contToSend = cont)

                    if (curTail.next.compareAndSet(null, nodeToEnqueue)) {
                        tail.compareAndSet(curTail, nodeToEnqueue)
                    }
                }
                return
            } else {
                val curHeadNext = curHead.next.value

                if (curHeadNext != null && head.compareAndSet(curHead, curHeadNext)) {
                    curHeadNext.contToReceive!!.resume(element)
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
            val curTail = tail.value
            val curHead = head.value

            if (curTail == curHead || !tail.value.isSender) {
                return suspendCoroutine { cont ->
                    val nodeToEnqueue = Node<E>(null, false, contToReceive = cont)

                    if (curTail.next.compareAndSet(null, nodeToEnqueue)) {
                        tail.compareAndSet(curTail, nodeToEnqueue)
                    }
                }
            } else {
                val curHeadNext = curHead.next.value

                if (curHeadNext != null && head.compareAndSet(curHead, curHeadNext)) {
                    curHeadNext.contToSend!!.resume(Unit)
                    return curHeadNext.x!!
                }
            }
        }
    }
}

class Node<E>(
    val x: E? = null,
    val isSender: Boolean,
    val contToSend: Continuation<Unit>? = null,
    val contToReceive: Continuation<E>? = null
) {
    val next = atomic<Node<E>?>(null)
}