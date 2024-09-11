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
    private val head: AtomicRef<Node<E>> = atomic(Node(null, null))
    private val tail: AtomicRef<Node<E>> = atomic(Node(null, null))

    /**
     * Sends the specified [element] to this channel, suspending if there is no waiting
     * [receive] invocation on this channel.
     */
    suspend fun send(element: E) {
        while (true) {
            val curHead = head.value
            val curTail = tail.value
            if (curHead == curTail || curTail.operation.value == Operation.SEND) {
                if (!corut(curTail, Node(Operation.SEND, element))) {
                    return
                }
            } else {
                val curNext = curHead.next.value ?: continue
                if (curNext.cor.value != null && head.compareAndSet(curHead, curNext)) {
                    val curVal = curNext.element.value
                    curNext.element.compareAndSet(curVal, element)
                    curNext.cor.value!!.resume(false)
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
            if (curHead == curTail || curTail.operation.value == Operation.RECEIVE) {
                val node = Node<E>(Operation.RECEIVE, null)
                if (!corut(curTail, node)) {
                    return node.element.value!!
                }
            } else {
                val curNext = curHead.next.value ?: continue
                if (curNext.cor.value != null && head.compareAndSet(curHead, curNext)) {
                    curNext.cor.value!!.resume(false)
                    return curNext.element.value!!
                }
            }
        }
    }

    private suspend fun corut(curTail: Node<E>, node: Node<E>): Boolean {
        return suspendCoroutine sc@{ cor ->
            node.cor.value = cor
            if (curTail.next.compareAndSet(null, node)) {
                tail.compareAndSet(curTail, node)
            } else {
                tail.compareAndSet(curTail, curTail.next.value!!)
                cor.resume(true)
                return@sc
            }
        }
    }

    enum class Operation {
        SEND,
        RECEIVE
    }

    private class Node<E>(type: Operation?, elem: E?) {
        val operation: AtomicRef<Operation?> = atomic(type)
        val element: AtomicRef<E?> = atomic(elem)
        val next: AtomicRef<Node<E>?> = atomic(null)
        val cor: AtomicRef<Continuation<Boolean>?> = atomic(null)
    }
}