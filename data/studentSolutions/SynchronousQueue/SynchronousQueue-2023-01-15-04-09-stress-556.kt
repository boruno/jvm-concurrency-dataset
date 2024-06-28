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
        val newNode: Node<E> = Node(true, null)
        head = atomic(newNode)
        tail = atomic(newNode)
    }

    /**
     * Sends the specified [element] to this channel, suspending if there is no waiting
     * [receive] invocation on this channel.
     */
    suspend fun send(element: E) {
        while (true) {
            val curHead = head.value
            val curTail = tail.value
            if (curTail.type || curHead == curTail) {
                if (check(curTail, Node(false, element))) return
                else continue
            }
            val next = curHead.next.value ?: continue
            if (predicate(curHead, curTail)) continue
            if (!next.type || next.continuation == null || !head.compareAndSet(curHead, next))
                continue
            next.value.compareAndSet(null, element)
            next.continuation!!.resume(true)
            return
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
            if (!curTail.type || curHead == curTail) {
                val node: Node<E> = Node(false, null)
                if (check(curTail, node)) return node.value.value!!
                else continue
            }
            val next = curHead.next.value ?: continue
            if (predicate(curHead, curTail)) continue
            val element = next.value.value ?: continue
            if (next.continuation == null || !head.compareAndSet(curHead, next))
                continue
            next.value.compareAndSet(element, null)
            next.continuation!!.resume(true)
            return element
        }
    }

    private fun predicate(curHead: Node<E>?, curTail: Node<E>?): Boolean {
        return curHead != head.value || curHead == tail.value || curTail != tail.value
    }

    private suspend fun check(curTail: Node<E>, node: Node<E>): Boolean {
        return suspendCoroutine sc@{ continuation ->
            node.continuation = continuation
            if (curTail.next.compareAndSet(null, node)) {
                tail.compareAndSet(curTail, node)
            } else {
                tail.compareAndSet(curTail, curTail.next.value!!)
                continuation.resume(false)
                return@sc
            }
        }
    }

    class Node<E>(val type: Boolean, v: E?) {
        // true for Sender, false for Receiver
        val value: AtomicRef<E?> = atomic(v)
        val next: AtomicRef<Node<E>?> = atomic(null)
        var continuation: Continuation<Boolean>? = null
    }
}