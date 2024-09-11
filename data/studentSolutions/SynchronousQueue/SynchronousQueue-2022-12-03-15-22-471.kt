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

/**
 * @author :Цветков Николай
 */

class SynchronousQueue<E> {
    private val senders = MSQueue<Pair<Continuation<Boolean>, E>>() // pair = continuation + element
    private val receivers = MSQueue<Continuation<E>>()

    /**
     * Sends the specified [element] to this channel, suspending if there is no waiting
     * [receive] invocation on this channel.
     */
    suspend fun send(element: E) {
        var res: Boolean = false
        var put = false
        while (true) {
            if (!receivers.isEmpty()) {
                val r = receivers.dequeue()
                r!!.resume(element)
                return
            } else {
                res = suspendCoroutine<Boolean> sc@{ cont ->
                    if (!receivers.isEmpty()) {
                        cont.resume(true)
                        val r = receivers.dequeue()
                        r!!.resume(element)
                        return@sc
                    } else {
                        senders.enqueue(cont to element)
                    }
                }
                if (res) {
                    return
                } else {
                    continue
                }
            }
        }
    }

    /**
     * Retrieves and removes an element from this channel if there is a waiting [send] invocation on it,
     * suspends the caller if this channel is empty.
     */
    suspend fun receive(): E {
        var res: E? = null;
        var put = false
        while (true) {
            if (!senders.isEmpty()) {
                val (s, elem) = senders.dequeue()!!
                s.resume(true)
                return elem
            } else {
                res = suspendCoroutine sc@{ cont ->

                    if (!senders.isEmpty()) {
                        val (s, elem) = senders.dequeue()!!
                        cont.resume(elem)
                        s.resume(true);
                        return@sc
                    } else {
                            receivers.enqueue(cont)
                    }
                }
                if (res != null) {
                    return res
                } else {
                    continue
                }
            }
        }
    }
}

/*

val res = suspendCoroutine<Any> sc@ { cont ->
  ...
  if (shouldRetry()) {
    cont.resume(RETRY)
    return@sc
  }
  ...
}
if (res === RETRY) continue
 */

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
            val newNode = Node(x);
            val curTail = tail.value;
            if (curTail.next.compareAndSet(null, newNode)) {
                tail.compareAndSet(curTail, newNode)
                return;
            } else {
                tail.compareAndSet(curTail, curTail.next.value as Node<E>)
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
            var curHead = head.value;
            var curNext = curHead.next.value;
            if (curNext === null) {
                return null
            } else {
                if (head.compareAndSet(curHead, curNext)) {
                    return curNext.x
                }
            }
        }
    }

    fun isEmpty(): Boolean {
        while (true) {
            val curTail = tail.value;
            if (curTail.next.value == null) {
                break;
            }
            tail.compareAndSet(curTail, curTail.next.value as Node<E>)
        }
        return tail.compareAndSet(head.value, tail.value)
    }
}

private class Node<E>(val x: E?) {
    val next = atomic<Node<E>?>(null)
}