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
        val dummy = Node<E>(null, Operation.DUMMY)
        head = atomic(dummy)
        tail = atomic(dummy)
    }

    /**
     * Sends the specified [element] to this channel, suspending if there is no waiting
     * [receive] invocation on this channel.
     */
    suspend fun send(element: E) {
        val node = Node<E>(null, Operation.SEND)
        while (true) {
            val curHead = head.value
            val curTail = tail.value
            if (curHead == curTail || curTail.isSend()) {
                if (enqueueAndSuspend(curTail, node)) {
                    return
                }
            } else {
                val curNext = curHead.next.value ?: continue
                if (curNext.coroutine != null && head.compareAndSet(curHead, curNext)) {
                    curNext.element.compareAndSet(curNext.element.value, element)
                    curNext.coroutine!!.resume(false)
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
        val node = Node<E>(null, Operation.RECEIVE)
        while (true) {
            val curHead = head.value
            val curTail = tail.value
            if (curHead == curTail || curTail.isReceive()) {
                if (enqueueAndSuspend(curTail, node)) {
                    return node.element.value!!
                }
            } else {
                val curNext = curHead.next.value ?: continue
                if (curNext.coroutine != null && head.compareAndSet(curHead, curNext)) {
                    curNext.coroutine!!.resume(false)
                    return curNext.element.value!!
                }
            }
        }
    }

    private suspend fun enqueueAndSuspend(curTail: Node<E>, node: Node<E>): Boolean {
        return !suspendCoroutine sc@{ cont ->
            node.coroutine = cont
            if (curTail.next.compareAndSet(null, node)) {
                tail.compareAndSet(curTail, node)
            } else {
                tail.compareAndSet(curTail, curTail.next.value!!)
                cont.resume(true)
                return@sc
            }
        }
    }
}

private enum class Operation {
    SEND, RECEIVE, DUMMY
}

private class Node<E>(element: E?, val operation: Operation) {
    val next = atomic<Node<E>?>(null)
    var coroutine: Continuation<Boolean>? = null
    val element = atomic(element)

    fun isSend(): Boolean {
        return operation == Operation.SEND
    }

    fun isReceive(): Boolean {
        return operation == Operation.RECEIVE
    }

}