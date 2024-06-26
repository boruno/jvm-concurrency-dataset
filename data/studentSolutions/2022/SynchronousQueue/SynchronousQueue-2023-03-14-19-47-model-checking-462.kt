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
        val singleInit = InitNode<E>()
        head = atomic(singleInit)
        tail = atomic(singleInit)
    }

    /**
     * Sends the specified [element] to this channel, suspending if there is no waiting
     * [receive] invocation on this channel.
     */
    suspend fun send(element: E): Unit {
        while (true) {
            val currentHead = head.value
            val currentTail = tail.value

            if (currentHead == currentTail || currentTail is Sender) {
                val res = suspendCoroutine<Any?> sc@{ cont ->
                    if (!currentTail.nextNode.compareAndSet(null, Sender(element, cont, null))) {
                        cont.resume(Retry<E>())
                        return@sc
                    }
                    tail.compareAndSet(currentTail, currentTail.nextNode.value!!)
                }

                if (res !is Retry<*>)
                    break

                continue
            }

            if (!head.compareAndSet(currentHead, currentHead.nextNode.value!!))
                continue

            currentHead.nextNode.value!!.value.value = element
            currentHead.nextNode.value!!.coroutine.value!!.resume(Unit)
            break
        }
    }

    /**
     * Retrieves and removes an element from this channel if there is a waiting [send] invocation on it,
     * suspends the caller if this channel is empty.
     */
    suspend fun receive(): E {
        while (true) {
            val currentHead = head.value
            val currentTail = tail.value
            if (currentHead == currentTail || currentTail is Receiver) {
                val res = suspendCoroutine<Any?> sc@{ cont ->
                    if (!currentTail.nextNode.compareAndSet(null, Receiver(null, cont, null))) {
                        cont.resume(Retry<E>())
                        return@sc
                    }
                    tail.compareAndSet(currentTail, currentTail.nextNode.value!!)
                }
                if (res !is Retry<*>)
                    return currentTail.nextNode.value!!.value.value!!

                continue
            }

            if (!head.compareAndSet(currentHead, currentHead.nextNode.value!!))
                continue

            currentHead.nextNode.value!!.coroutine.value!!.resume(Unit)
            return currentHead.nextNode.value!!.value.value!!

        }
    }
}

private abstract class Node<E>(value: E?, coroutine: Continuation<Any?>?, nextNode: Node<E>?) {
    val nextNode = atomic(nextNode)
    val value = atomic(value)
    val coroutine = atomic(coroutine)
}

private class Sender<E>(value: E, coroutine: Continuation<Any?>?, nextNode: Node<E>?) :
    Node<E>(value, coroutine, nextNode)

private class Receiver<E>(value: E?, coroutine: Continuation<Any?>?, nextNode: Node<E>?) :
    Node<E>(value, coroutine, nextNode)

private class InitNode<E> : Node<E>(null, null, null)
private class Retry<E> : Node<E>(null, null, null)