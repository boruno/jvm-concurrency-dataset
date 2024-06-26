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
            if (curHead == curTail || curTail.operation.value == "SEND") {
                if (!coroutine(curTail, Node("SEND", element))) {
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
            //val curHead = head.value
            //val curTail = tail.value
            if (head.value == tail.value || tail.value.operation.value == "RECEIVE") {
                val node = Node<E>("RECEIVE", null)
                if (!coroutine(tail.value, node)) {
                    return node.element.value!!
                }
            } else {
                val curNext = head.value.next.value ?: continue
                if (curNext.cor.value != null && head.compareAndSet(head.value, curNext)) {
                    curNext.cor.value!!.resume(false)
                    return curNext.element.value!!
                }
            }
        }
    }

    private suspend fun coroutine(curTail: Node<E>, node: Node<E>): Boolean {
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

    private class Node<E>(type: String?, elem: E?) {
        val operation: AtomicRef<String?> = atomic(type)
        val element: AtomicRef<E?> = atomic(elem)
        val next: AtomicRef<Node<E>?> = atomic(null)
        val cor: AtomicRef<Continuation<Boolean>?> = atomic(null)
    }
}