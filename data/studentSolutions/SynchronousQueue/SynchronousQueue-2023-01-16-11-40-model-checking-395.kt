import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlin.coroutines.*

/**
 * An element is transferred from sender to receiver only when [send] and [receive]
 * invocations meet in time (rendezvous), so [send] suspends until another coroutine
 * invokes [receive] and [receive] suspends until another coroutine invokes [send].
 */
class SynchronousQueue<E> {

    private val senders   = MSQueue<Pair<Continuation<Unit>, E>>() // pair = continuation + element
    private val receivers = MSQueue<Continuation<E>>()

    /**
     * Sends the specified [element] to this channel, suspending if there is no waiting
     * [receive] invocation on this channel.
     */
    suspend fun send(element: E): Unit {
        val r = receivers.dequeue()
        if (r != null) {
            r.resume(element)
        } else {
            suspendCoroutine<Unit> { cont ->
                senders.enqueue(cont to element)
            }
        }

        // val cor = Coroutine(element)
        // queue.enqueue(cor)
        // // suspend(cor)
        // suspendCoroutine<Unit> { cont ->
        //     senders.add(cont to element)
        // }
    }

    /**
     * Retrieves and removes an element from this channel if there is a waiting [send] invocation on it,
     * suspends the caller if this channel is empty.
     */
    suspend fun receive(): E {
        val pair = senders.dequeue()
        if (pair != null) {
            val (s, elem) = pair
            s.resume(Unit)
            return elem
        } else {
            return suspendCoroutine { cont ->
                receivers.enqueue(cont)
            }
        }

        // var elem: Coroutine<E>? = null
        // do {
        //     elem = queue.dequeue()
        // } while (elem == null)
        // return elem.element
    }
}

class MSQueue<E> {
    private val head: AtomicRef<Node<E>>
    private val tail: AtomicRef<Node<E>>

    init {
        val dummy = Node<E>(null)
        head = atomic(dummy)
        tail = atomic(dummy)
    }

    /**
     * Adds the specified element [x] to the queue.
     */
    fun enqueue(x: E) {
        while (true) {
            val node = Node(x)
            val tailNode = tail.value
            if (tailNode.next.compareAndSet(null, node)) {
                tail.compareAndSet(tailNode, node)
                return
            } else {
                val tailNodeNext = tailNode.next.value
                if (tailNodeNext != null) {
                    tail.compareAndSet(tailNode, tailNodeNext)
                }
            }
        }
    }

    /**
     * Retrieves the first element from the queue
     * and returns it; returns `null` if the queue
     * is empty.
     */
    fun dequeue(): E? {
        while (true) {
            val headNode = head.value
            val headNodeNext = headNode.next.value
            if (headNodeNext == null) {
                return null
            } else if (head.compareAndSet(headNode, headNodeNext)) {
                return headNodeNext.x
            }
        }
    }

    fun isEmpty(): Boolean {
        return head.value.next.value == null
    }
}

private class Node<E>(val x: E?) {
    val next = atomic<Node<E>?>(null)
}
