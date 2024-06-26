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
        val dummy = Node<E>(null, true)
        head = atomic(dummy)
        tail = atomic(dummy)
    }

    /**
     * Sends the specified [element] to this channel, suspending if there is no waiting
     * [receive] invocation on this channel.
     */
    suspend fun send(element: E): Unit {
        while (true) {
            val curHead = head.value
            val curTail = tail.value
            if (curTail == curHead || curTail.isSender) {
                enqueueAndSuspend(curTail, element, true) ?: continue
                return
            } else {
                val headNext = curHead.next.value
                if (curTail != tail.value || curHead != head.value || headNext == null) {
                    continue
                }

                if (!headNext.isSender && head.compareAndSet(curHead, headNext)) {
//                    headNext.continuation!!.resume(element)
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
            if (curTail == curHead || !curTail.isSender) {
                val x = enqueueAndSuspend(curTail, null, false)
                if (x != null) {
                    return x
                } else {
                    continue
                }
            } else {
                val headNext = curHead.next.value
                if (curTail != tail.value || curHead != head.value || headNext == null) {
                    continue
                }

                val res = headNext.x
                if (headNext.isSender && head.compareAndSet(curHead, headNext)) {
//                    headNext.continuation!!.resume(res)
                    return res!!
                }
            }
        }
    }

    private suspend fun enqueueAndSuspend(curTail: Node<E>, x: E?, isSender: Boolean): E? {
//        return suspendCoroutine sc@{ continuation ->
//            val newTail = Node(x, continuation, isSender)
//            if (curTail.next.compareAndSet(null, newTail)) {
//                tail.compareAndSet(curTail, newTail)
//            } else {
//                tail.compareAndSet(curTail, curTail.next.value!!)
//                continuation.resume(null)
//                return@sc
//            }
//        }
        val newTail = Node(x, isSender)
        if (curTail.next.compareAndSet(null, newTail)) {
            tail.compareAndSet(curTail, newTail)
        } else {
            tail.compareAndSet(curTail, curTail.next.value!!)
        }
        return tail.value.x
    }
}

private class Node<E>(val x: E?, val isSender: Boolean) {
    val next: AtomicRef<Node<E>?> = atomic(null)
}