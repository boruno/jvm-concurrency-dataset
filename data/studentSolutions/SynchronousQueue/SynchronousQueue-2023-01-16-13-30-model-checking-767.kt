import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlin.coroutines.*

/**
 * An element is transferred from sender to receiver only when [send] and [receive]
 * invocations meet in time (rendezvous), so [send] suspends until another coroutine
 * invokes [receive] and [receive] suspends until another coroutine invokes [send].
 */
class SynchronousQueue<E> {

    // private val senders   = MSQueue<Pair<Continuation<Unit>, E>>()
    // private val receivers = MSQueue<Continuation<E>>()
    private val queue = MSQueue<Either<E>>()

    /**
     * Sends the specified [element] to this channel, suspending if there is no waiting
     * [receive] invocation on this channel.
     */
    suspend fun send(element: E): Unit {
        while (true) {
            val tail = queue.tail.value
            val head = queue.head.value
            if (tail == head || tail.x!!.isSender()) {
                val res = suspendCoroutine { cont ->
                    cont.resume(queue.enqueue(tail, Either<E>(0, cont to element, null)))
                }
                if (res == false) continue
            } else {
                val r = queue.dequeue(head)
                if (r != null) {
                    r.receiver!!.resume(element)
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
            val tail = queue.tail.value
            val head = queue.head.value
            if (tail == head || tail.x!!.isReceiver()) {
                val res = suspendCoroutine { cont ->
                    cont.resume(queue.enqueue(tail, Either<E>(1, null, cont)))                    
                }
                if (res == false) continue
            } else {
                val pair = queue.dequeue(head)
                if (pair != null) {
                    val (s, elem) = pair.sender!!
                    s.resume(Unit)
                    return elem
                }
            }
        }
    }

    private class Either<E>(val op: Int,
                            val sender:   Pair<Continuation<Unit>, E>?,
                            val receiver: Continuation<E>?) {
        fun isSender()   = op == 0
        fun isReceiver() = op == 1
    }
}

class MSQueue<E> {
    val head: AtomicRef<Node<E>>
    val tail: AtomicRef<Node<E>>

    init {
        val dummy = Node<E>(null)
        head = atomic(dummy)
        tail = atomic(dummy)
    }

    /**
     * Adds the specified element [x] to the queue.
     */
    fun enqueue(tailNode: Node<E>, x: E): Boolean {
        val node = Node(x)
        if (tailNode.next.compareAndSet(null, node)) {
            tail.compareAndSet(tailNode, node)
            return true
        } else {
            val tailNodeNext = tailNode.next.value
            if (tailNodeNext != null) {
                tail.compareAndSet(tailNode, tailNodeNext)
            }
            return false
        }
    }

    /**
     * Retrieves the first element from the queue
     * and returns it; returns `null` if the queue
     * is empty.
     */
    fun dequeue(headNode: Node<E>): E? {
        val headNodeNext = headNode.next.value
        if (headNodeNext == null) {
            return null
        } else if (head.compareAndSet(headNode, headNodeNext)) {
            return headNodeNext.x
        } else {
            return null
        }
    }

    fun isEmpty(): Boolean {
        return head.value.next.value == null
    }

    class Node<E>(val x: E?) {
        val next = atomic<Node<E>?>(null)
    }
}
