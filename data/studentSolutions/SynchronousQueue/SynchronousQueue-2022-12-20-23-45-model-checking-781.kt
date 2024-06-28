import kotlinx.atomicfu.*
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * An element is transferred from sender to receiver only when [send] and [receive]
 * invocations meet in time (rendezvous), so [send] suspends until another coroutine
 * invokes [receive] and [receive] suspends until another coroutine invokes [send].
 */
class SynchronousQueue<E> {
    private val valueQ = MSQueue<E>()
    private val coroutinesQ = MSQueue<Continuation<E>>()

    /**
     * Sends the specified [element] to this channel, suspending if there is no waiting
     * [receive] invocation on this channel.
     */
    suspend fun send(element: E): Unit {
        val c = coroutinesQ.dequeue()
        if(c == null) {
            valueQ.enqueue(element)
        } else {
            c.resume(element)
        }
    }

    /**
     * Retrieves and removes an element from this channel if there is a waiting [send] invocation on it,
     * suspends the caller if this channel is empty.
     */
    suspend fun receive(): E {
        val x = valueQ.dequeue()
        return if(x == null) {
            suspendCoroutine { sc -> coroutinesQ.enqueue(sc)  }
        } else {
            x;
        }
    }
}


// Nikolay Rulev
private class MSQueue<E> {
    private val head: AtomicRef<Node<E>>
    private val tail: AtomicRef<Node<E>>

    init {
        val dummy = Node<E>(null)
        head = atomic(dummy)
        tail = atomic(dummy)
    }

    fun enqueue(x: E) {
        val newNode = Node(x)
        while (true) {
            val curTail = tail.value
            val next = curTail.next.value
            if (curTail == tail.value) {
                if (next == null) {
                    if(curTail.next.compareAndSet(null, newNode)) {
                        tail.compareAndSet(curTail, newNode)
                        break
                    }
                } else {
                    tail.compareAndSet(curTail, next)
                }
            }
        }
    }

    fun dequeue(): E? {
        while (true) {
            val curHead = head.value
            val curTail = tail.value
            val next = curHead.next.value
            if (curHead == head.value) {
                if(curHead == curTail) {
                    if (next == null) {
                        return null
                    } else {
                        // CAS(&Q–>Tail, tail, <next.ptr, tail.count+1>)
                        tail.compareAndSet(curTail, next)
                    }
                } else {
                    // CAS(&Q–>Head, head, <next.ptr, head.count+1>)
                    if (head.compareAndSet(curHead, next!!)) {
                        return next.x
                    }
                }

            }
        }
    }

    fun isEmpty(): Boolean {
        return head.value == tail.value
    }
}

private class Node<E>(val x: E?) {
    val next = atomic<Node<E>?>(null)
}