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

    private val head: AtomicRef<Node<Task<E>>>
    private val tail: AtomicRef<Node<Task<E>>>

    init {
        val dummy = Node<Task<E>>(null)
        head = atomic(dummy)
        tail = atomic(dummy)
    }

    /**
     * Sends the specified [element] to this channel, suspending if there is no waiting
     * [receive] invocation on this channel.
     */
    suspend fun send(element: E): Unit {
        while (true) {
            val t = tail.value
            val h = head.value
            if (t == h || t.x == null || t.x is Send<E>) {
                suspendCoroutine<Unit?> {
                    val nextNode = Node<Task<E>>(Send(it, element))
                    if (t.next.compareAndSet(null, nextNode)) {
                        tail.compareAndSet(t, nextNode)
                    } else {
                        tail.compareAndSet(t, t.next.value!!)
                        it.resume(null)
                    }
                } ?: continue
                return
            } else {
                val currentHeadNext = h.next.value ?: continue
                if (head.compareAndSet(h, currentHeadNext)) {
                    when (currentHeadNext.x) {
                        is Receive<E> -> currentHeadNext.x.continuation.resume(element)
                        else -> throw IllegalStateException()
                    }
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
            val t = tail.value
            val h = head.value
            if (t == h || t.x == null || t.x is Receive<E>) {
                val result = suspendCoroutine<E?> {
                    val nextNode = Node<Task<E>>(Receive(it))
                    if (t.next.compareAndSet(null, nextNode)) {
                        tail.compareAndSet(t, nextNode)
                    } else {
                        tail.compareAndSet(t, t.next.value!!)
                        it.resume(null)
                    }
                } ?: continue
                return result
            } else {
                val currentHeadNext = h.next.value ?: continue
                if (head.compareAndSet(h, currentHeadNext)) {
                    when (currentHeadNext.x) {
                        is Send<E> -> {
                            currentHeadNext.x.continuation.resume(Unit)
                            return currentHeadNext.x.value
                        }
                        else -> throw IllegalStateException()
                    }
                }
            }
        }
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
            val nextNode = Node(x)
            val currentTail = tail.value
            if (currentTail.next.compareAndSet(null, nextNode)) {
                tail.compareAndSet(currentTail, nextNode)
                return
            } else {
                tail.compareAndSet(currentTail, currentTail.next.value!!)
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
            val currentHead = head.value
            val currentHeadNext = currentHead.next.value ?: return null

            if (head.compareAndSet(currentHead, currentHeadNext)) {
                return currentHeadNext.x
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

sealed class Task<E>
class Receive<E>(val continuation: Continuation<E>) : Task<E>()

class Send<E>(val continuation: Continuation<Unit>, val value: E) : Task<E>()