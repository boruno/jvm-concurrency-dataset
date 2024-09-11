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
        val dummy = Node<E>(null, null)
        head = atomic(Node<E>(null, null))
        tail = atomic(Node<E>(null, null))
    }
    /**
     * Sends the specified [element] to this channel, suspending if there is no waiting
     * [receive] invocation on this channel.
     */
    suspend fun send(element: E): Unit {
        while (true) {
            val curHead = head.value
            val curTail = tail.value
            if (curHead == curTail || curTail.isSend.value == true) {
                val check = suspendCoroutine sc@{ continuation ->
                    val sendNode = Node(true, element)
                    sendNode.continuation.value = continuation
                    if (curTail.next.compareAndSet(null, sendNode)) {
                        tail.compareAndSet(curTail, sendNode)
                    } else {
                        tail.compareAndSet(curTail, curTail.next.value!!)
                        continuation.resume(true)
                        return@sc
                    }
                }
                if (!check) {
                    return
                }
            } else {
                val nextNode = curHead.next.value ?: continue
                if (head.compareAndSet(curHead, nextNode)) {
                    val curVal = nextNode.element.value
                    nextNode.element.compareAndSet(curVal, element)
                    nextNode.continuation.value!!.resume(false)
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
            if (curHead == curTail || curTail.isSend.value == false) {
                val receiveNode = Node<E>(false, null)
                val check = suspendCoroutine sc@{ continuation ->
                    receiveNode.continuation.value = continuation
                    if (curTail.next.compareAndSet(null, receiveNode)) {
                        tail.compareAndSet(curTail, receiveNode)
                    } else {
                        tail.compareAndSet(curTail, curTail.next.value!!)
                        continuation.resume(true)
                        return@sc
                    }
                }
                if (!check) {
                    return receiveNode.element.value!!
                }
            } else {
                val nextNode = curHead.next.value ?: continue
                if (head.compareAndSet(curHead, nextNode)) {
                    nextNode.continuation.value!!.resume(false)
                    return nextNode.element.value!!
                }
            }
        }
    }
}

private class Node<E>(isSend: Boolean?, elem: E?) {
    val next: AtomicRef<Node<E>?> = atomic(null)
    val element: AtomicRef<E?> = atomic(elem)
    val isSend: AtomicRef<Boolean?> = atomic(isSend)
    val continuation: AtomicRef<Continuation<Boolean>?> = atomic(null)
}


