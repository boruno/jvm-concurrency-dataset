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

    companion object {
        private const val RETRY = "RETRY"
    }

    private val head: AtomicRef<Node>
    private val tail: AtomicRef<Node>

    init {
        val dummy = Node(cont = null, element = null)
        head = atomic(dummy)
        tail = atomic(dummy)
    }

    /**
     * Sends the specified [element] to this channel, suspending if there is no waiting
     * [receive] invocation on this channel.
     */
    suspend fun send(element: E) {
        while (true) {
            val currentTail = tail.value
            val currentHead = head.value
            if (currentTail == currentHead || currentTail.isSender()) {
                if(addAndSuspend(currentTail, element) !== RETRY) break
            } else {
                if(pollAndResume(currentHead) !== RETRY) break
            }
        }
    }

    /**
     * Retrieves and removes an element from this channel if there is a waiting [send] invocation on it,
     * suspends the caller if this channel is empty.
     */
    suspend fun receive(): E {
        while (true) {
            val currentTail = tail.value
            val currentHead = head.value
            val res = if (currentTail == currentHead || currentTail.isReceiver()) {
                addAndSuspend(currentTail, element = null)
            } else {
                pollAndResume(currentHead)
            }
            if(res !== RETRY) return res as E
        }
    }

    private suspend fun addAndSuspend(currentTail: Node, element: Any?) = suspendCoroutine sc@{ cont ->
        val node = Node(cont, element)
        if (currentTail.next.compareAndSet(null, node)) {
            tail.compareAndSet(currentTail, node)
        } else {
            tail.compareAndSet(currentTail, currentTail.next.value!!)
            cont.resume(RETRY)
            return@sc
        }
    }

    private fun pollAndResume(currentHead: Node): Any? {
        val headNext = currentHead.next.value ?: return RETRY
        if (head.compareAndSet(currentHead, headNext)) {
            headNext.cont!!.resume(headNext.element)
            return headNext.element
        }
        return RETRY
    }

    private class Node(
        val cont: Continuation<Any?>?,
        val element: Any?
    ) {
        val next = atomic<Node?>(null)
        fun isSender() = element != null
        fun isReceiver() = !isSender()
    }
}