import kotlin.coroutines.Continuation
import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
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
        val dummy = Node<E>(null, null, ElementType.DUMMY)
        head = atomic(dummy)
        tail = atomic(dummy)
    }

    private fun enqueue(oldTail: Node<E>, node: Node<E>): Boolean {
        val oldNext = oldTail.next.value
        if (tail.value == oldTail) {
            if (oldNext == null) {
                if (oldTail.next.compareAndSet(null, node)) {
                    tail.compareAndSet(oldTail, node)
                    return true
                }
            } else {
                tail.compareAndSet(oldTail, oldNext)
            }
        }
        return false
    }

    private fun dequeue(oldHead: Node<E>): Node<E>? {
        val oldHeadNext = oldHead.next.value
        if (oldHead == head.value) {
            val ret = oldHeadNext!!
            if (head.compareAndSet(oldHead, oldHeadNext)) {
                return ret
            }
        }
        return null
    }

    /**
     * Sends the specified [element] to this channel, suspending if there is no waiting
     * [receive] invocation on this channel.
     */
    suspend fun send(element: E): Unit {
        while (true) {
            val oldTail = tail.value
            val oldHead = head.value
            if (oldHead.next.value == null || oldTail.elementType != ElementType.RECEIVE) {
                val result = suspendCoroutine { cont ->
                    if (!enqueue(oldTail, Node(element, cont, ElementType.SEND))) {
                        cont.resume(Retry())
                    }
                }

                if (result is Retry) {
                    continue
                } else {
                    return
                }
            } else {
                val node = dequeue(oldHead) ?: continue
                node.cont!!.resume(element)
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
            val oldTail = tail.value
            val oldHead = head.value
            if (oldHead.next.value == null || oldTail.elementType != ElementType.SEND) {
                val result = suspendCoroutine { cont ->
                    if (!enqueue(oldTail, Node(null, cont, ElementType.RECEIVE))) {
                        cont.resume(Retry())
                    }
                }

                if (result is Retry) {
                    continue
                } else {
                    return result as E
                }
            } else {
                val node = dequeue(oldHead) ?: continue
                node.cont!!.resume(Unit)
                return node.element!!
            }
        }
    }
}

enum class ElementType {
    SEND,
    RECEIVE,
    DUMMY
}

class Retry

class Node<E>(val element: E?, val cont: Continuation<Any?>?, val elementType: ElementType) {
    val next = atomic<Node<E>?>(null)
}
