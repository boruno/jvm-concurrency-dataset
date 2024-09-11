import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import java.lang.Exception
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
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
        val dummy = Node.DummyNode<E>()
        head = atomic(dummy)
        tail = atomic(dummy)
    }

    /**
     * Sends the specified [element] to this channel, suspending if there is no waiting
     * [receive] invocation on this channel.
     */
    suspend fun send(element: E) {
        while (true) {
            val curTail = tail.value
            val curHead = head.value

            if (curHead.next.value == null || curTail is Node.SenderNode<*>) {
                try {
                    enqueueAndSuspendSender(element, curTail)
                    return
                } catch (e: UnsuccessfulOperationException) {
                    continue
                }
            } else {
                val isSuccess = dequeueAndResumeSender(element, curHead)
                if (isSuccess) return
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

            if (curHead.next.value == null || curTail is Node.ReceiverNode<*>) {
                try {
                    return enqueueAndSuspendReceiver(curTail)
                } catch (e: UnsuccessfulOperationException) {
                    continue
                }
            } else {
                dequeueAndResumeReceiver(curHead) ?: continue
            }
        }
    }

    private suspend fun enqueueAndSuspendSender(element: E, curTail: Node<E>) {
        suspendCoroutine { cont ->
            val node = Node.SenderNode(element, cont)

            if (curTail.next.compareAndSet(null, node)) {
                tail.compareAndSet(curTail, node)
            } else {
                tail.compareAndSet(curTail, curTail.next.value!!)
                cont.resumeWithException(UnsuccessfulOperationException)
            }
        }
    }

    private suspend fun enqueueAndSuspendReceiver(curTail: Node<E>): E {
        return suspendCoroutine { cont ->
            val node = Node.ReceiverNode(cont)

            if (curTail.next.compareAndSet(null, node)) {
                tail.compareAndSet(curTail, node)
            } else {
                tail.compareAndSet(curTail, curTail.next.value!!)
                cont.resumeWithException(UnsuccessfulOperationException)
            }
        }
    }

    private fun dequeueAndResumeSender(element: E, curHead: Node<E>): Boolean {
        val curHeadNext = curHead.next.value ?: return false
        if (curHeadNext !is Node.ReceiverNode) return false
        return if (head.compareAndSet(curHead, curHeadNext)) {
            curHeadNext.continuation?.resume(element)
            true
        } else {
            false
        }
    }

    private fun dequeueAndResumeReceiver(curHead: Node<E>): E? {
        val curHeadNext = curHead.next.value ?: return null
        if (curHeadNext !is Node.SenderNode) return null
        return if (head.compareAndSet(curHead, curHeadNext)) {
            curHeadNext.continuation?.resume(Unit)
            curHeadNext.x
        } else {
            null
        }
    }
}

sealed class Node<E> {

    val next = atomic<Node<E>?>(null)

    data class SenderNode<E>(val x: E?, val continuation: Continuation<Unit>?) : Node<E>()

    data class ReceiverNode<E>(val continuation: Continuation<E>?) : Node<E>()

    class DummyNode<E> : Node<E>()
}

object UnsuccessfulOperationException : Exception()
