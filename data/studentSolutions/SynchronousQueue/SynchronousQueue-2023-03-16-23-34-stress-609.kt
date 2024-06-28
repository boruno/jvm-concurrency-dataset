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
    private val head: AtomicRef<Node>
    private val tail: AtomicRef<Node>

    private val RETRY: Any

    init {
        val dummy = Node(Pair(null, null))
        head = atomic(dummy)
        tail = atomic(dummy)
        RETRY = Any()
    }

    /**
     * Sends the specified [element] to this channel, suspending if there is no waiting
     * [receive] invocation on this channel.
     */
    suspend fun send(element: E): Unit {
        while (true) {
            val curTail = tail.value
            val curHead = head.value
            if (curHead.next.value == null || curHead.next.value!!.x.second != null) {
                val res = suspendCoroutine { cont ->
                    val shouldRetry: Boolean
                    val node = Node(Pair(cont, element))
                    if (curTail.next.compareAndSet(null, node)) {
                        tail.compareAndSet(curTail, node)
                        shouldRetry = false
                    } else {
                        tail.compareAndSet(curTail, curTail.next.value!!)
                        shouldRetry = true
                    }

                    if (shouldRetry) {
                        cont.resume(RETRY)
                    }
                }

                if (res == RETRY) {
                    continue
                }

                return
            } else {
                val res: Node?
                val newHead = curHead.next.value!!
                if (head.compareAndSet(curHead, newHead)) {
                    res = newHead
                } else {
                    res = null
                }

                if (res != null) {
                    res.x.first?.resume(element)
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
            val curTail = tail.value
            val curHead = head.value
            if (curHead.next.value == null || curHead.next.value!!.x.second == null) {
                val res = suspendCoroutine { cont ->
                    if (shouldRetry(curTail, cont, null)) {
                        cont.resume(RETRY)
                    }
                }

                if (res == RETRY) {
                    continue
                }

                return res as E
            } else {
                val res: Node?
                val newHead = curHead.next.value!!
                if (head.compareAndSet(curHead, newHead)) {
                    res = newHead
                } else {
                    res = null
                }

                if (res != null) {
                    res.x.first?.resume(Unit)
                    return res.x.second as E
                }
            }
        }
    }

    fun shouldRetry(t: Node, x: Continuation<Any?>, e: E?): Boolean {
        val node = Node(Pair(x, e));
        if (t.next.compareAndSet(null, node)) {
            tail.compareAndSet(t, node);
            return true;
        } else {
            tail.compareAndSet(t, t.next.value!!)
            return false;
        }
    }
}

class Node(val x: Pair<Continuation<Any?>?, Any?>) {
    val next = atomic<Node?>(null)
}