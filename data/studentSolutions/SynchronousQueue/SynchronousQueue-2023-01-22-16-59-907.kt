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
        val dummy = Node<E>()
        head = atomic(dummy)
        tail = atomic(dummy)
    }

    /**
     * Sends the specified [element] to this channel, suspending if there is no waiting
     * [receive] invocation on this channel.
     */
    suspend fun send(element: E) {
        while (true) {
            val curHead = head.value
            val curTail = tail.value
            if (curHead == curTail || curTail.type == Type.SEND) {
                val result = suspendCoroutine sc@ { cont ->
                    val newTail = Node(Type.SEND, cont, element)
                    if (!curTail.next.compareAndSet(null, newTail)) {
                        tail.compareAndSet(curTail, curTail.next.value!!)
                        cont.resume(Status.RETRY)
                        return@sc
                    }
                    tail.compareAndSet(curTail, newTail)
                }
                if (result != Status.RETRY) return
            } else {
                val headNext = curHead.next.value ?: continue
                if (head.compareAndSet(curHead, headNext)) {
//                    headNext.operationArg.getAndSet(element)
                    headNext.operation!!.resume(Status.OK)
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
            val curTail = tail.value
            if (curHead == curTail || curTail.type == Type.RECEIVE) {
                val result = suspendCoroutine sc@ { cont ->
                    val newTail = Node<E>(Type.RECEIVE, cont)
                    if (!curTail.next.compareAndSet(null, newTail)) {
                        tail.compareAndSet(curTail, curTail.next.value!!)
                        cont.resume(Status.RETRY)
                        return@sc
                    }
                    tail.compareAndSet(curTail, newTail)
                }
                if (result != Status.RETRY) return curTail.next.value!!.operationArg.value!!
            } else {
                val headNext = curHead.next.value ?: continue
                if (head.compareAndSet(curHead, headNext)) {
                    headNext.operation!!.resume(Status.OK)
                    return headNext.operationArg.value!!
                }
            }
        }
    }
}

private enum class Type {
    UNDEFINED, SEND, RECEIVE
}

private enum class Status {
    OK, RETRY
}

private class Node<E>(val type: Type = Type.UNDEFINED, val operation: Continuation<Status>? = null, value: E? = null) {
    val next = atomic<Node<E>?>(null)
    val operationArg = atomic(value)
}
