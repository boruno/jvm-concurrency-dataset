import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic

/**
 * An element is transferred from sender to receiver only when [send] and [receive]
 * invocations meet in time (rendezvous), so [send] suspends until another coroutine
 * invokes [receive] and [receive] suspends until another coroutine invokes [send].
 */
class SynchronousQueue<E> {
    private val head: AtomicRef<Node<E>>
    private val tail: AtomicRef<Node<E>>

    init {
        val dummy = Node<E>(null, false)
        head = atomic(dummy)
        tail = atomic(dummy)
    }

    /**
     * Sends the specified [element] to this channel, suspending if there is no waiting
     * [receive] invocation on this channel.
     */
    suspend fun send(element: E): Unit {
        val offer = Node(element, false)
        while (true) {
            val currentTail = tail.value
            val currentHead = head.value
            if (currentHead === currentTail || !currentTail.isSender) {
                val node = currentTail.next.value
                if (currentTail === tail.value) {
                    if (node !== null) {
                        tail.compareAndSet(currentTail, node)
                    } else if (currentTail.next.compareAndSet(node, offer)) {
                        tail.compareAndSet(currentTail, offer)
                        return
                    }
                }
            } else {
                val node = currentHead.next
                if (currentTail == tail.value && currentHead == head.value && node.value !== null) {
                    val success = node.compareAndSet(null, Node(element, node.value!!.isSender))
                    head.compareAndSet(currentHead, node.value!!)
                    if (success) return
                }
            }
        }
    }

    /**
     * Retrieves and removes an element from this channel if there is a waiting [send] invocation on it,
     * suspends the caller if this channel is empty.
     */
    suspend fun receive(): E {
        val offer = Node(null as E?, true)
        while (true) {
            val currentTail = tail.value
            val currentHead = head.value
            if (currentHead === currentTail || currentTail.isSender) {
                val node = currentTail.next.value
                if (currentTail === tail.value) {
                    if (node !== null) {
                        tail.compareAndSet(currentTail, node)
                    } else if (currentTail.next.compareAndSet(node, offer)) {
                        tail.compareAndSet(currentTail, offer)
                        while (offer.x === null) continue
                        if (currentHead === offer) head.compareAndSet(currentHead, offer)
                        return offer.x
                    }
                }
            } else {
                val node = currentHead.next
                if (node.value!!.x != null) {
                    val success = node.compareAndSet(node.value, null)
                    head.compareAndSet(currentHead, node.value!!)
                    if (success) return node.value!!.x!!
                }
            }
        }
    }
}

private class Node<E>(val x: E?, val isSender: Boolean) {

    val next = atomic<Node<E>?>(null)
}