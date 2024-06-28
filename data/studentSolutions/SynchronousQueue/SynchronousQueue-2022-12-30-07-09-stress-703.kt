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
    private var dummy: Node<E> = Node(null, null)
    private val head: AtomicRef<Node<E>> = atomic(dummy)
    private val tail: AtomicRef<Node<E>> = atomic(dummy)

    /**
     * Sends the specified [element] to this channel, suspending if there is no waiting
     * [receive] invocation on this channel.
     */
    suspend fun send(element: E) {
        while (true) {
            val curHead: Node<E> = head.value
            val curTail: Node<E> = tail.value
            if ((curHead == curTail || curTail.operation.value == "SEND") && !coroutine(curTail, Node("SEND", element))) {
                return
            } else if (curHead.next.value!!.cor != null && head.compareAndSet(curHead, curHead.next.value!!)) {
                curHead.next.value!!.element.compareAndSet(curHead.next.value!!.element.value, element)
                curHead.next.value!!.cor!!.resume(false)
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
            val curHead: Node<E> = head.value
            val curTail: Node<E> = tail.value
            if (curHead == curTail || tail.value.operation.value == "RECEIVE") {
                val node: Node<E> = Node("RECEIVE", null)
                if (!coroutine(curTail, node)) return node.element.value!!
            } else if (curHead.next.value!!.cor != null && head.compareAndSet(curHead, curHead.next.value!!)) {
                curHead.next.value!!.cor!!.resume(false)
                return curHead.next.value!!.element.value!!
            }
        }
    }

    private suspend fun coroutine(curTail: Node<E>, node: Node<E>): Boolean =
        suspendCoroutine { cor ->
            node.cor = cor
            when {
                curTail.next.compareAndSet(null, node) -> tail.compareAndSet(curTail, node)
                else -> {
                    tail.compareAndSet(curTail, curTail.next.value!!)
                    cor.resume(true)
                }
            }
        }

    private class Node<E>(type: String?, elem: E?) {
        val operation: AtomicRef<String?> = atomic(type)
        val element: AtomicRef<E?> = atomic(elem)
        val next: AtomicRef<Node<E>?> = atomic(null)
        var cor: Continuation<Boolean>? = null
    }
}