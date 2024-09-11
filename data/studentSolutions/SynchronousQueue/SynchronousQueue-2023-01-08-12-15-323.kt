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
    // Implementing with MS-Queue
    private val head: AtomicRef<Node<E>>
    private val tail: AtomicRef<Node<E>>
    private val retry: Retry = Retry()

    init {
        val dummy = Node<E>(element = null, ActionType.Dummy)
        head = atomic(dummy)
        tail = atomic(dummy)
    }

    /**
     * Sends the specified [element] to this channel, suspending if there is no waiting
     * [receive] invocation on this channel.
     */
    suspend fun send(element: E): Unit {
       while (true) {
            val currentTail = tail.value
            val currentHead = head.value
            val node = Node(element, ActionType.Send)

           if (currentTail != tail.value)
               continue
            // If the channel is empty, we can enqueue only (no possibility for rendezvous with someone);
            // If the tail (first node is dummy) of the channel (queue) is Send operation owner,
            // then we also cannot rendezvous with someone, so just enqueue and wait for the chance.
            //
            // Otherwise, there are some receivers in the channel, so let's help them with receiving.
            if (currentTail == currentHead || currentTail.isSender()) {
                val isSuccessful = enqueueAndSuspend(currentTail, node)
                if (isSuccessful)
                    return
            } else {
                if (currentHead != head.value)
                    continue

                val isDequeueSuccessful = dequeueAndResume(currentHead, node)
                if (isDequeueSuccessful)
                    return
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
            val node = Node<E>(element = null, ActionType.Receive)

            if (currentTail == currentHead || currentTail.isReceiver()) {
                if (currentTail != tail.value)
                    continue

                val isSuccessful = enqueueAndSuspend(currentTail, node)
                if (isSuccessful)
                    return node.element!!
            } else {
                if (currentHead != head.value)
                    continue

                val isDequeueSuccessful = dequeueAndResume(currentHead, node)
                if (!isDequeueSuccessful)
                    continue

                return currentHead.next.value?.element!!
            }
        }
    }

    private suspend fun enqueueAndSuspend(currentTail: Node<E>, node: Node<E>): Boolean {
        val res = suspendCoroutine sc@ { cont ->
            node.setCoroutine(cont)

            val currentTailNext = currentTail.next

            if (!currentTailNext.compareAndSet(null, node)) {
                // Switching to the next node and retry.
                tail.compareAndSet(currentTail, currentTailNext.value!!)
                cont.resume(retry)
                return@sc
            } else {
                // Moving tail pointer to the just added node.
                tail.compareAndSet(currentTail, node)
            }
        }

        return res != retry
    }

    private fun dequeueAndResume(currentHead: Node<E>, requestNode: Node<E>): Boolean {
        val node: Node<E>?

        val currentHeadNext = currentHead.next.value ?: return false

        if (!head.compareAndSet(currentHead, currentHeadNext)) {
            return false
        }

        node = currentHeadNext

        if (requestNode.isSender() && node.isReceiver()) {
            val result = requestNode.element!!
            node.updateElement(result)
            node.coroutine?.resume(result)
            return true
        } else if (node.isSender() && requestNode.isReceiver()) {
            val result = node.element!!
            requestNode.updateElement(result)
            node.coroutine?.resume(result)
            return true
        } else {
            return false
        }
    }
}

private class Node<E>(var element: E?, val action: ActionType) {
    var coroutine: Continuation<Any>? = null
        private set

    val next: AtomicRef<Node<E>?> = atomic(null)

    fun setCoroutine(c: Continuation<Any>) {
        coroutine = c
    }

    fun updateElement(e: E?) {
        element = e
    }

    fun isSender(): Boolean {
        return action == ActionType.Send
    }

    fun isReceiver(): Boolean {
        return action == ActionType.Receive
    }
}

private enum class ActionType {
    Send,
    Receive,
    Dummy,
}

private class Retry()