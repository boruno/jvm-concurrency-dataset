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
            val value = tail.x
            val empty = queue.isEmpty()
            if (!empty && value == null) {
                continue
            }
            if (queue.isEmpty() || value!!.isSender()) {
                val res = suspendCoroutine<Boolean> sc@{ cont ->
                    val res2 = queue.enqueue(tail, Either<E>(0, cont to element, null))
                    if (res2 == false) {
                        cont.resume(false)
                        return@sc
                    }
                }
                if (res == true) return
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
            val value = tail.x
            val empty = queue.isEmpty()
            if (!empty && value == null) {
                continue
            }
            if (queue.isEmpty() || value!!.isReceiver()) {
                val res = suspendCoroutine sc@{ cont ->
                    val res2 = queue.enqueue(tail, Either<E>(1, null, cont))
                    if (res2 == false) {
                        cont.resume(null)
                        return@sc
                    }
                }
                if (res != null) return res
            } else {
                val pair = queue.dequeue(head)
                if (pair != null) {
                    val (s, elem) = pair.sender!!
                    s.resume(true)
                    return elem
                }
            }
        }
    }

    private class Either<E>(val op: Int,
                            val sender:   Pair<Continuation<Boolean>, E>?,
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

