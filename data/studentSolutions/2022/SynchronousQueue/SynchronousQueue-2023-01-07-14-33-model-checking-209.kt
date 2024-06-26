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
    private val sendersQ = MSQueue<Pair<Continuation<Unit>, E>>()
    private val receiversQ = MSQueue<Continuation<E>>()
    val smallLock = atomic(false)
    /**
     * Sends the specified [element] to this channel, suspending if there is no waiting
     * [receive] invocation on this channel.
     */
    suspend fun send(element: E): Unit {
        while (!smallLock.compareAndSet(false, update = true)) { continue }
        val rec = receiversQ.dequeue()
        if(rec != null) {
            rec.resume(element)
            return
        } else {
            return suspendCoroutine { cont ->
                run {
                    sendersQ.enqueue(Pair(cont, element))
                    smallLock.compareAndSet(true, update = false)
                }
            }
        }
    }

    /**
     * Retrieves and removes an element from this channel if there is a waiting [send] invocation on it,
     * suspends the caller if this channel is empty.
     */
    suspend fun receive(): E {
        while (!smallLock.compareAndSet(false, update = true)) { continue }
        val res = sendersQ.dequeue()
        if(res != null) {
            res.first.resume(Unit)
            smallLock.compareAndSet(true, update = false)
            return res.second
        } else {
            return suspendCoroutine { cont -> receiversQ.enqueue(cont) }
        }
    }
}

// Nikolay Rulev
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

    /**
     * Retrieves the first element from the queue
     * and returns it; returns `null` if the queue
     * is empty.
     */
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